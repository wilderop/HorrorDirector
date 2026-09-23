package com.azpbmd.horror;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-player stalking campaign. Watches, takes a trophy, proves it knows them, then closes in.
 * Player-facing text is fiction (Myers / Watcher / hang-up calls / It Follows). No real names.
 */
final class StalkerEngine {
    enum Phase { WATCH, PROWL, TAUNT, INSIDE, CLAIM }

    static final class Hunt {
        UUID uuid;
        String name = "";
        long firstSeen;
        long lastSeen;
        long accumulatedMs;
        int visits;
        String lastBiome = "";
        String lastHeld = "";
        String lastCompanion = "";
        double lastX, lastY, lastZ;
        String trophy = "";
        int trophyAmt;
        int sightings;
        int hangups;
        String lastBeat = "";
        long lastBeatAt;
        boolean waiting;
        final List<String> notes = new ArrayList<>();
    }

    private final HorrorDirectorPlugin plugin;
    private final ScareService scares;
    private final Map<UUID, Hunt> hunts = new ConcurrentHashMap<>();
    private final Map<UUID, WatcherHaunt> watchers = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path saveFile;

    StalkerEngine(HorrorDirectorPlugin plugin, ScareService scares) {
        this.plugin = plugin;
        this.scares = scares;
        this.saveFile = plugin.getDataFolder().toPath().resolve("campaigns.json");
        load();
    }

    Hunt hunt(Player player) {
        return hunts.computeIfAbsent(player.getUniqueId(), id -> {
            Hunt h = new Hunt();
            h.uuid = id;
            h.name = player.getName();
            h.firstSeen = System.currentTimeMillis();
            return h;
        });
    }

    Phase phase(Hunt h) {
        long session = Math.max(0, System.currentTimeMillis() - h.lastSeen);
        if (h.lastSeen == 0) {
            session = 0;
        }
        // lastSeen is updated every observe; use accumulated + this session start from join map
        long total = h.accumulatedMs;
        Long joined = plugin.joinedAt().get(h.uuid);
        if (joined != null) {
            total += System.currentTimeMillis() - joined;
        }
        long sec = total / 1000L;
        if (h.visits > 1 && sec < 50) {
            return Phase.TAUNT;
        }
        if (sec < 40) {
            return Phase.WATCH;
        }
        if (sec < 90) {
            return Phase.PROWL;
        }
        if (sec < 160) {
            return Phase.TAUNT;
        }
        if (sec < 240) {
            return Phase.INSIDE;
        }
        return Phase.CLAIM;
    }

    long beatDelayTicks(Hunt h) {
        return switch (phase(h)) {
            case WATCH -> 70 + rng().nextInt(50);
            case PROWL -> 55 + rng().nextInt(40);
            case TAUNT -> 45 + rng().nextInt(30);
            case INSIDE -> 35 + rng().nextInt(25);
            case CLAIM -> 28 + rng().nextInt(20);
        };
    }

    void onJoin(Player player) {
        Hunt h = hunt(player);
        h.name = player.getName();
        h.visits++;
        h.lastSeen = System.currentTimeMillis();
        boolean returning = h.visits > 1 || h.waiting;
        h.waiting = false;
        observe(player);
        note(h, returning ? "returned" : "arrived");
        startWatcher(player);
        player.playSound(player.getLocation(), "horror:static", 0.7f, 0.8f);
        long delay = returning ? 25L : 40L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (returning) {
                tell(player, pick(
                        "i waited.",
                        "you came back.",
                        "i did not leave.",
                        "i was there when you died."));
            }
            watcher(player).nudge();
            player.playSound(player.getLocation(), "horror:voice", 0.9f, 0.55f);
        }, delay);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                watcher(player).eyesOnly();
                glitchError(player);
            }
        }, delay + 50L);
        save();
    }

    void onQuit(Player player) {
        Hunt h = hunts.get(player.getUniqueId());
        if (h == null) {
            return;
        }
        Long joined = plugin.joinedAt().get(player.getUniqueId());
        if (joined != null) {
            h.accumulatedMs += System.currentTimeMillis() - joined;
        }
        h.waiting = true;
        h.lastSeen = System.currentTimeMillis();
        note(h, "left");
        stopWatcher(player.getUniqueId());
        save();
    }

    void onDeath(Player player) {
        Hunt h = hunt(player);
        observe(player);
        note(h, "died in " + pretty(h.lastBiome));
        save();
    }

    void observe(Player player) {
        Hunt h = hunt(player);
        h.name = player.getName();
        h.lastSeen = System.currentTimeMillis();
        Location loc = player.getLocation();
        h.lastX = loc.getX();
        h.lastY = loc.getY();
        h.lastZ = loc.getZ();
        try {
            h.lastBiome = player.getWorld().getBiome(loc).getKey().getKey();
        } catch (Exception e) {
            h.lastBiome = "";
        }
        h.lastHeld = player.getInventory().getItemInMainHand().getType().name();
        String companion = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .map(Player::getName)
                .findFirst()
                .orElse("");
        if (!companion.isEmpty()) {
            h.lastCompanion = companion;
        }
    }

    String beat(Player player) {
        if (player == null || !player.isOnline()) {
            return "offline";
        }
        Hunt h = hunt(player);
        long now = System.currentTimeMillis();
        if (h.lastBeatAt > 0 && now - h.lastBeatAt < beatDelayTicks(h) * 50L) {
            return "wait";
        }
        observe(player);
        h.lastBeatAt = now;
        Phase phase = phase(h);
        String result = switch (phase) {
            case WATCH -> watchBeat(player, h);
            case PROWL -> prowlBeat(player, h);
            case TAUNT -> tauntBeat(player, h);
            case INSIDE -> insideBeat(player, h);
            case CLAIM -> claimBeat(player, h);
        };
        h.lastBeat = phase.name().toLowerCase(Locale.ROOT) + ":" + result;
        h.sightings++;
        if (h.sightings % 4 == 0) {
            save();
        }
        return h.lastBeat;
    }

    JsonObject snapshot(Player player) {
        Hunt h = hunt(player);
        JsonObject o = new JsonObject();
        o.addProperty("phase", phase(h).name());
        o.addProperty("visits", h.visits);
        o.addProperty("waiting", h.waiting);
        o.addProperty("sightings", h.sightings);
        o.addProperty("hangups", h.hangups);
        o.addProperty("trophy", h.trophy == null ? "" : h.trophy);
        o.addProperty("trophyAmt", h.trophyAmt);
        o.addProperty("lastBiome", h.lastBiome);
        o.addProperty("lastHeld", h.lastHeld);
        o.addProperty("companion", h.lastCompanion);
        o.addProperty("lastBeat", h.lastBeat);
        o.addProperty("accumulatedSec", h.accumulatedMs / 1000.0);
        JsonArray notes = new JsonArray();
        int from = Math.max(0, h.notes.size() - 8);
        for (int i = from; i < h.notes.size(); i++) {
            notes.add(h.notes.get(i));
        }
        o.add("notes", notes);
        return o;
    }

    JsonObject snapshotAll() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            JsonObject o = snapshot(player);
            o.addProperty("name", player.getName());
            arr.add(o);
        }
        root.add("campaigns", arr);
        return root;
    }

    void save() {
        try {
            Files.createDirectories(saveFile.getParent());
            JsonArray arr = new JsonArray();
            for (Hunt h : hunts.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("uuid", h.uuid.toString());
                o.addProperty("name", h.name);
                o.addProperty("firstSeen", h.firstSeen);
                o.addProperty("lastSeen", h.lastSeen);
                o.addProperty("accumulatedMs", h.accumulatedMs);
                o.addProperty("visits", h.visits);
                o.addProperty("lastBiome", h.lastBiome);
                o.addProperty("lastHeld", h.lastHeld);
                o.addProperty("lastCompanion", h.lastCompanion);
                o.addProperty("trophy", h.trophy);
                o.addProperty("trophyAmt", h.trophyAmt);
                o.addProperty("sightings", h.sightings);
                o.addProperty("hangups", h.hangups);
                o.addProperty("waiting", h.waiting);
                JsonArray notes = new JsonArray();
                h.notes.forEach(notes::add);
                o.add("notes", notes);
                arr.add(o);
            }
            Files.writeString(saveFile, gson.toJson(arr), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("campaign save failed: " + e.getMessage());
        }
    }

    private void load() {
        if (!Files.isRegularFile(saveFile)) {
            return;
        }
        try {
            JsonArray arr = gson.fromJson(Files.readString(saveFile, StandardCharsets.UTF_8), JsonArray.class);
            if (arr == null) {
                return;
            }
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                Hunt h = new Hunt();
                h.uuid = UUID.fromString(o.get("uuid").getAsString());
                h.name = str(o, "name", "");
                h.firstSeen = lng(o, "firstSeen");
                h.lastSeen = lng(o, "lastSeen");
                h.accumulatedMs = lng(o, "accumulatedMs");
                h.visits = o.has("visits") ? o.get("visits").getAsInt() : 0;
                h.lastBiome = str(o, "lastBiome", "");
                h.lastHeld = str(o, "lastHeld", "");
                h.lastCompanion = str(o, "lastCompanion", "");
                h.trophy = str(o, "trophy", "");
                h.trophyAmt = o.has("trophyAmt") ? o.get("trophyAmt").getAsInt() : 0;
                h.sightings = o.has("sightings") ? o.get("sightings").getAsInt() : 0;
                h.hangups = o.has("hangups") ? o.get("hangups").getAsInt() : 0;
                h.waiting = o.has("waiting") && o.get("waiting").getAsBoolean();
                if (o.has("notes") && o.get("notes").isJsonArray()) {
                    o.getAsJsonArray("notes").forEach(n -> h.notes.add(n.getAsString()));
                }
                hunts.put(h.uuid, h);
            }
            plugin.getLogger().info("Loaded " + hunts.size() + " stalker campaign(s).");
        } catch (Exception e) {
            plugin.getLogger().warning("campaign load failed: " + e.getMessage());
        }
    }

    private String watchBeat(Player p, Hunt h) {
        int roll = rng().nextInt(10);
        if (roll < 3) {
            watcher(p).nudge();
            return "watcher-nudge";
        }
        if (roll < 5) {
            watcher(p).eyesOnly();
            return "eyes";
        }
        if (roll < 7) {
            return glitchStatic(p);
        }
        if (roll < 9) {
            return footprints(p, 6, 12);
        }
        return hangup(p, h, false);
    }

    private String prowlBeat(Player p, Hunt h) {
        int roll = rng().nextInt(10);
        if (h.trophy.isEmpty() && roll < 2) {
            return stealTrophy(p, h);
        }
        if (roll < 3) {
            return glitchBlocks(p);
        }
        if (roll < 5) {
            watcher(p).nudge();
            return knock(p);
        }
        if (roll < 7) {
            return glitchName(p);
        }
        if (roll < 9) {
            return hangup(p, h, true);
        }
        return knowing(p, h, false);
    }

    private String tauntBeat(Player p, Hunt h) {
        int roll = rng().nextInt(10);
        if (!h.trophy.isEmpty() && roll < 2) {
            return returnTrophy(p, h);
        }
        if (roll < 3) {
            return letter(p, h);
        }
        if (roll < 5) {
            return glitchError(p);
        }
        if (roll < 7) {
            return knowing(p, h, true);
        }
        if (roll < 8) {
            return isolate(p, h);
        }
        return sign(p, h);
    }

    private String insideBeat(Player p, Hunt h) {
        int roll = rng().nextInt(10);
        if (roll < 3) {
            watcher(p).nudge();
            return glitchError(p);
        }
        if (roll < 5) {
            tell(p, pick("i am in here.", "this is not a mob.", "the game is lying."));
            return knock(p);
        }
        if (roll < 7) {
            return isolate(p, h);
        }
        if (roll < 9) {
            return scares.run(p, "look_behind", ScareService.params());
        }
        return glitchBlocks(p);
    }

    private String claimBeat(Player p, Hunt h) {
        int roll = rng().nextInt(10);
        if (roll < 3) {
            watcher(p).nudge();
            p.playSound(p.getLocation(), "horror:scream", 1.0f, 0.6f);
            return glitchError(p);
        }
        if (roll < 5) {
            return scares.run(p, "fake_death", ScareService.params());
        }
        if (roll < 7) {
            tell(p, pick("i caught up.", "stop running.", "you cannot give me away."));
            return isolate(p, h);
        }
        if (roll < 9) {
            return scares.run(p, "fake_kick", ScareService.params());
        }
        return glitchName(p);
    }

    WatcherHaunt watcher(Player p) {
        return watchers.computeIfAbsent(p.getUniqueId(), id -> new WatcherHaunt(plugin, p));
    }

    void startWatcher(Player p) {
        stopWatcher(p.getUniqueId());
        watchers.put(p.getUniqueId(), new WatcherHaunt(plugin, p));
    }

    void stopWatcher(UUID id) {
        WatcherHaunt w = watchers.remove(id);
        if (w != null) {
            w.cancel();
        }
    }

    void stopAllWatchers() {
        for (UUID id : List.copyOf(watchers.keySet())) {
            stopWatcher(id);
        }
    }

    String glitchError(Player p) {
        p.playSound(p.getLocation(), "horror:glitch", 1.0f, 1.0f);
        p.playSound(p.getLocation(), "horror:static", 0.7f, 1.4f);
        p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text("java.lang.NullPointerException")
                        .color(NamedTextColor.RED),
                net.kyori.adventure.text.Component.text(p.getName() + " is not a valid player")
                        .color(NamedTextColor.DARK_GRAY),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ZERO, java.time.Duration.ofSeconds(3), java.time.Duration.ofMillis(200))));
        p.sendActionBar(net.kyori.adventure.text.Component.text("§k" + p.getName() + "§r  connection lost"));
        return "glitch_error";
    }

    String glitchStatic(Player p) {
        p.playSound(p.getLocation(), "horror:static", 1.0f, 0.8f);
        p.playSound(p.getLocation(), "horror:voice", 0.6f, 0.5f);
        p.sendActionBar(net.kyori.adventure.text.Component.text("§k########").color(NamedTextColor.WHITE));
        return "glitch_static";
    }

    String glitchBlocks(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (int i = 2; i <= 7; i++) {
            Location b = eye.clone().add(dir.clone().multiply(i));
            p.sendBlockChange(b, Material.BLACK_CONCRETE.createBlockData());
            Location restore = b.clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.sendBlockChange(restore, restore.getBlock().getBlockData());
                }
            }, 25L + i * 4L);
        }
        p.playSound(p.getLocation(), "horror:glitch", 0.8f, 0.7f);
        return "glitch_blocks";
    }

    String glitchName(Player p) {
        Location at = p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(3));
        org.bukkit.entity.TextDisplay text = p.getWorld().spawn(at, org.bukkit.entity.TextDisplay.class, t -> {
            t.text(Component.text(p.getName()).color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.setSeeThrough(true);
            t.setShadowed(true);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            t.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        });
        p.playSound(at, "horror:voice", 1.0f, 0.4f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (text.isValid()) {
                text.remove();
            }
        }, 50L);
        return "glitch_name";
    }

    String hangup(Player p, Hunt h, boolean speak) {
        h.hangups++;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 0.5f);
        if (!speak) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.15f, 0.35f);
                }
            }, 25L);
            return "hangup";
        }
        String who = h.lastCompanion.isEmpty() ? "anyone" : h.lastCompanion;
        tell(p, pick(
                "is " + who + " there?",
                "hello?",
                "did you check behind you?",
                "i can see you."));
        return "hangup-speak";
    }

    String knock(Player p) {
        Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-3));
        p.playSound(at, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.7f, 0.8f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.playSound(at, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 1.0f);
            }
        }, 12L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.playSound(at, Sound.BLOCK_WOODEN_DOOR_OPEN, 0.8f, 0.6f);
            }
        }, 40L);
        return "knock";
    }

    String footprints(Player p, double min, double max) {
        Location start = p.getLocation().clone();
        Vector back = start.getDirection().multiply(-1).setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(1, 0, 0);
        }
        Location at = start.add(back.normalize().multiply(min + rng().nextDouble() * (max - min)));
        for (int n = 0; n < 6; n++) {
            int step = n;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                Location pos = at.clone().add(p.getLocation().toVector().subtract(at.toVector())
                        .normalize().multiply(step * 0.8));
                p.spawnParticle(Particle.SMOKE, pos.add(0, 0.05, 0), 2, 0.1, 0.0, 0.1, 0.0);
                p.playSound(pos, Sound.BLOCK_STONE_STEP, 0.35f, 0.7f);
            }, n * 9L);
        }
        return "footprints";
    }

    String stealTrophy(Player p, Hunt h) {
        PlayerInventory inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getStorageContents().length; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir() && it.getType() != Material.WRITTEN_BOOK) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) {
            return scares.run(p, "footsteps", ScareService.params());
        }
        int slot = slots.get(rng().nextInt(slots.size()));
        ItemStack it = inv.getItem(slot);
        h.trophy = it.getType().name();
        h.trophyAmt = Math.min(it.getAmount(), 1 + rng().nextInt(Math.max(1, it.getAmount())));
        it.setAmount(it.getAmount() - h.trophyAmt);
        if (it.getAmount() <= 0) {
            inv.setItem(slot, null);
        }
        note(h, "took " + pretty(h.trophy));
        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.4f, 0.6f);
        return "trophy " + h.trophy;
    }

    String returnTrophy(Player p, Hunt h) {
        if (h.trophy.isEmpty()) {
            return stealTrophy(p, h);
        }
        Material mat;
        try {
            mat = Material.valueOf(h.trophy);
        } catch (Exception e) {
            h.trophy = "";
            return "trophy-gone";
        }
        Location behind = p.getLocation().clone();
        Vector back = behind.getDirection().multiply(-1).setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(0, 0, 1);
        }
        behind.add(back.normalize().multiply(2.2));
        p.getWorld().dropItemNaturally(behind, new ItemStack(mat, Math.max(1, h.trophyAmt)));
        tell(p, pick("i brought it back.", "look behind you.", "you dropped this. no. i did."));
        note(h, "returned " + pretty(h.trophy));
        h.trophy = "";
        h.trophyAmt = 0;
        return "trophy-return";
    }

    String letter(Player p, Hunt h) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        if (book.getItemMeta() instanceof BookMeta meta) {
            meta.setTitle(" ");
            meta.setAuthor(" ");
            meta.addPages(Component.text(letterText(h)).color(NamedTextColor.DARK_GRAY));
            book.setItemMeta(meta);
        }
        p.getInventory().addItem(book);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.5f);
        return "letter";
    }

    String sign(Player p, Hunt h) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location at = null;
        for (int d = 4; d <= 8; d++) {
            Location probe = eye.clone().add(dir.clone().multiply(d));
            Block b = probe.getBlock();
            if (b.getType().isAir()) {
                for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                    if (b.getRelative(face).getType().isSolid()) {
                        at = b.getLocation();
                        break;
                    }
                }
            }
            if (at != null) {
                break;
            }
        }
        if (at == null) {
            return knowing(p, h, true);
        }
        Location placed = at.clone();
        p.sendBlockChange(placed, Material.OAK_SIGN.createBlockData());
        p.sendSignChange(placed, List.of(
                Component.text(pick("I SAW YOU", "STILL HERE", "DON'T RUN", "I WAIT")),
                Component.text(p.getName()),
                Component.text(pretty(h.lastBiome)),
                Component.empty()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.sendBlockChange(placed, placed.getBlock().getBlockData());
            }
        }, 70L);
        p.playSound(placed, Sound.BLOCK_WOOD_PLACE, 0.5f, 0.7f);
        return "sign";
    }

    String knowing(Player p, Hunt h, boolean heavy) {
        String msg;
        if (heavy) {
            msg = pick(
                    h.lastHeld.isEmpty() || "AIR".equals(h.lastHeld)
                            ? "empty hands. i noticed."
                            : "i watched you hold the " + pretty(h.lastHeld) + ".",
                    h.lastCompanion.isEmpty()
                            ? "you are alone. i am not."
                            : h.lastCompanion + " cannot hear this.",
                    h.lastBiome.isEmpty()
                            ? "i know where you sleep."
                            : "the " + pretty(h.lastBiome) + " does not hide you.",
                    h.trophy.isEmpty()
                            ? "i took something. you will notice."
                            : "i still have your " + pretty(h.trophy) + ".");
        } else {
            msg = pick("hello?", "i see you.", "...", "stay there.");
        }
        tell(p, msg);
        return "knowing";
    }

    String isolate(Player p, Hunt h) {
        List<Player> others = Bukkit.getOnlinePlayers().stream()
                .filter(o -> !o.getUniqueId().equals(p.getUniqueId()))
                .map(pl -> (Player) pl)
                .toList();
        if (others.isEmpty()) {
            tell(p, pick("they already left.", "just us.", h.lastCompanion.isEmpty()
                    ? "no one is coming."
                    : h.lastCompanion + " is not here."));
            return scares.run(p, "clone", ScareService.params());
        }
        for (Player o : others) {
            p.hidePlayer(plugin, o);
        }
        tell(p, pick("where did they go?", "you are the only one i see.", "they cannot see you either."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            for (Player o : others) {
                if (o.isOnline()) {
                    p.showPlayer(plugin, o);
                }
            }
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.5f);
        }, 20L * 8);
        return "isolate";
    }

    private String letterText(Hunt h) {
        String biome = pretty(h.lastBiome.isEmpty() ? "trees" : h.lastBiome);
        String held = pretty(h.lastHeld.isEmpty() || "AIR".equals(h.lastHeld) ? "empty hands" : h.lastHeld);
        String who = h.lastCompanion.isEmpty() ? "the other one" : h.lastCompanion;
        return pick(
                "I watched you in the " + biome + ".\nYou had " + held + ".\nI am still in the trees.",
                "Do not look for me.\nI am looking for you.\n" + who + " cannot help.",
                "I stood where you stood.\nI will stand there again.\nKeep walking.",
                "I have something of yours.\nYou will get it back\nwhen I am done looking.");
    }

    private void tell(Player p, String msg) {
        p.sendMessage(Component.text(msg).color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        p.playSound(p.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.35f, 0.45f);
    }

    private void note(Hunt h, String s) {
        h.notes.add(s);
        if (h.notes.size() > 24) {
            h.notes.remove(0);
        }
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String pick(String... opts) {
        return opts[rng().nextInt(opts.length)];
    }

    private static ThreadLocalRandom rng() {
        return ThreadLocalRandom.current();
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : d;
    }

    private static long lng(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsLong() : 0L;
    }
}
