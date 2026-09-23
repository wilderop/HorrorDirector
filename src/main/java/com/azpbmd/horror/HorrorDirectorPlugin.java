package com.azpbmd.horror;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HorrorDirectorPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private ScareService scares;
    private StalkerEngine engine;
    private AmbientDirector ambient;
    private DirectorHttpServer http;
    private String token = "";
    private NamespacedKey bookKey;
    private boolean spawnHunted;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bookKey = new NamespacedKey(this, "welcome_book");
        token = resolveToken();
        getConfig().set("http.token", token);
        saveConfig();
        writeTokenFile();

        scares = new ScareService(this);
        engine = new StalkerEngine(this, scares);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);

        for (World world : Bukkit.getWorlds()) {
            hauntWorld(world);
        }

        ambient = new AmbientDirector(this, scares);
        ambient.start();

        try {
            String bind = getConfig().getString("http.bind", "127.0.0.1");
            int port = getConfig().getInt("http.port", 30088);
            http = new DirectorHttpServer(this, bind, port, token, scares);
            http.start();
            getLogger().info("Director API on " + bind + ":" + port);
        } catch (IOException e) {
            getLogger().severe("Director HTTP failed: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (ambient != null) {
            ambient.stop();
        }
        if (engine != null) {
            engine.stopAllWatchers();
            engine.save();
        }
        if (http != null) {
            http.stop();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
    }

    public ScareService scares() {
        return scares;
    }

    public StalkerEngine engine() {
        return engine;
    }

    public Map<UUID, Long> joinedAt() {
        return joinedAt;
    }

    public boolean ambientEnabled() {
        return getConfig().getBoolean("ambient.enabled", true);
    }

    public void setAmbientEnabled(boolean enabled) {
        getConfig().set("ambient.enabled", enabled);
        saveConfig();
    }

    private String resolveToken() {
        String configured = getConfig().getString("http.token", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        Path file = Path.of(".director.token");
        try {
            if (Files.isRegularFile(file)) {
                String fromFile = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!fromFile.isEmpty()) {
                    return fromFile;
                }
            }
        } catch (IOException ignored) {
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void writeTokenFile() {
        Path file = Path.of(".director.token");
        try {
            Files.writeString(file, token + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("Could not write director token file: " + e.getMessage());
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        hauntWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joinedAt.put(player.getUniqueId(), System.currentTimeMillis());
        event.joinMessage(null);
        player.setPlayerTime(18000L, false);
        player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);

        if (getConfig().getBoolean("join.give-book", true)
                && !player.getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE)) {
            player.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
            player.openBook(Book.builder()
                    .title(Component.text(" "))
                    .author(Component.text(" "))
                    .pages(Component.text("Don't look behind you.")
                            .color(NamedTextColor.DARK_RED)
                            .decorate(TextDecoration.ITALIC))
                    .build());
        }

        if (engine != null) {
            engine.onJoin(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        if (engine != null) {
            engine.onDeath(event.getEntity());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        if (engine != null) {
            engine.onQuit(event.getPlayer());
        }
        joinedAt.remove(event.getPlayer().getUniqueId());
        scares.cleanup(event.getPlayer());
    }

    private void hauntWorld(World world) {
        double diameter = getConfig().getDouble("world.border-diameter", 60000.0);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(diameter);
        border.setWarningDistance(64);
        border.setDamageBuffer(8);
        border.setDamageAmount(0.4);

        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.REDUCED_DEBUG_INFO, getConfig().getBoolean("world.reduced-debug", true));
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
        world.setGameRule(GameRule.MOB_GRIEFING, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        world.setGameRule(GameRule.DO_INSOMNIA, true);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
        try {
            world.setGameRule(GameRule.LOCATOR_BAR, getConfig().getBoolean("world.locator-bar", false));
        } catch (Exception ignored) {
        }

        if (getConfig().getBoolean("world.eternal-night", true)
                && world.getEnvironment() == World.Environment.NORMAL) {
            world.setTime(18000L);
        }
        if (getConfig().getBoolean("world.thunder", true)
                && world.getEnvironment() == World.Environment.NORMAL) {
            world.setStorm(true);
            world.setThundering(true);
            world.setWeatherDuration(20 * 60 * 60 * 24);
            world.setThunderDuration(20 * 60 * 60 * 24);
        }
        world.setDifficulty(org.bukkit.Difficulty.HARD);
        world.setSpawnFlags(true, true);

        if (!spawnHunted && world.getEnvironment() == World.Environment.NORMAL) {
            spawnHunted = true;
            Bukkit.getScheduler().runTaskLater(this, () -> huntPaleSpawn(world), 40L);
        }
    }

    private void huntPaleSpawn(World world) {
        if (getConfig().getBoolean("world.spawn-locked", false)) {
            return;
        }
        int radius = getConfig().getInt("world.pale-garden-search-radius", 8000);
        Location origin = new Location(world, 0, 80, 0);
        Location found = firstBiome(world, origin, radius, "pale_garden", "dark_forest", "deep_dark", "mangrove_swamp");
        if (found == null) {
            getLogger().info("No haunted biome near origin; leaving default spawn.");
            return;
        }
        found.setY(world.getHighestBlockYAt(found) + 1);
        world.setSpawnLocation(found);
        getConfig().set("world.spawn-locked", true);
        getConfig().set("world.spawn-x", found.getBlockX());
        getConfig().set("world.spawn-z", found.getBlockZ());
        saveConfig();
        getLogger().info("Haunt spawn set to " + found.getBlockX() + " " + found.getBlockY() + " " + found.getBlockZ()
                + " biome=" + found.getBlock().getBiome());
    }

    private Location firstBiome(World world, Location origin, int radius, String... keys) {
        for (String key : keys) {
            Biome biome = Bukkit.getRegistry(Biome.class).get(NamespacedKey.minecraft(key));
            if (biome == null) {
                continue;
            }
            try {
                var result = world.locateNearestBiome(origin, radius, 64, 64, biome);
                if (result != null && result.getLocation() != null) {
                    return result.getLocation();
                }
            } catch (Exception e) {
                getLogger().warning("Biome search failed for " + key + ": " + e.getMessage());
            }
        }
        return null;
    }

    public JsonObject snapshot(Player player) {
        JsonObject o = new JsonObject();
        Location loc = player.getLocation();
        o.addProperty("name", player.getName());
        o.addProperty("uuid", player.getUniqueId().toString());
        o.addProperty("world", player.getWorld().getName());
        o.addProperty("env", player.getWorld().getEnvironment().name());
        o.addProperty("x", round(loc.getX()));
        o.addProperty("y", round(loc.getY()));
        o.addProperty("z", round(loc.getZ()));
        o.addProperty("yaw", round(loc.getYaw()));
        o.addProperty("pitch", round(loc.getPitch()));
        o.addProperty("health", round(player.getHealth()));
        o.addProperty("food", player.getFoodLevel());
        o.addProperty("air", player.getRemainingAir());
        o.addProperty("level", player.getLevel());
        o.addProperty("exp", round(player.getExp()));
        o.addProperty("gm", player.getGameMode().name());
        o.addProperty("sneaking", player.isSneaking());
        o.addProperty("sprinting", player.isSprinting());
        o.addProperty("swimming", player.isSwimming());
        o.addProperty("flying", player.isFlying());
        o.addProperty("blocking", player.isBlocking());
        try {
            o.addProperty("biome", player.getWorld().getBiome(loc).getKey().getKey());
        } catch (Exception e) {
            o.addProperty("biome", "unknown");
        }
        o.addProperty("held", player.getInventory().getItemInMainHand().getType().name());
        o.addProperty("helmet", player.getInventory().getHelmet() == null
                ? "AIR" : player.getInventory().getHelmet().getType().name());
        var target = player.getTargetBlockExact(6);
        o.addProperty("looking", target == null ? "AIR" : target.getType().name());
        Long joined = joinedAt.get(player.getUniqueId());
        o.addProperty("seconds", joined == null ? 0 : (System.currentTimeMillis() - joined) / 1000.0);
        o.addProperty("light", loc.getBlock().getLightLevel());
        if (engine != null) {
            o.add("campaign", engine.snapshot(player));
        }
        return o;
    }

    public JsonObject snapshotAll() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            arr.add(snapshot(player));
        }
        root.addProperty("count", arr.size());
        root.add("players", arr);
        return root;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("horror")) {
            return false;
        }
        if (args.length == 0) {
            sender.sendMessage("usage: /horror <type|types|ambient> [player|*] [k=v...]");
            return true;
        }
        if (args[0].equalsIgnoreCase("types")) {
            sender.sendMessage(String.join(", ", scares.types()));
            return true;
        }
        if (args[0].equalsIgnoreCase("ambient")) {
            if (args.length >= 2) {
                setAmbientEnabled(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true"));
            }
            sender.sendMessage("ambient=" + ambientEnabled());
            return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        List<Player> targets = new ArrayList<>();
        int paramStart = 1;
        if (args.length >= 2 && !args[1].contains("=")) {
            paramStart = 2;
            if (args[1].equals("*") || args[1].equalsIgnoreCase("all")) {
                targets.addAll(Bukkit.getOnlinePlayers());
            } else {
                Player found = Bukkit.getPlayerExact(args[1]);
                if (found == null) {
                    sender.sendMessage("no player " + args[1]);
                    return true;
                }
                targets.add(found);
            }
        } else if (sender instanceof Player player) {
            targets.add(player);
        } else {
            targets.addAll(Bukkit.getOnlinePlayers());
        }
        JsonObject params = ScareService.params();
        for (int i = paramStart; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) {
                params.addProperty(args[i].substring(0, eq), args[i].substring(eq + 1));
            }
        }
        if (targets.isEmpty()) {
            sender.sendMessage("nobody online");
            return true;
        }
        for (Player target : targets) {
            sender.sendMessage(target.getName() + ": " + scares.run(target, type, params));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("horror")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>(scares.types());
            out.add("types");
            out.add("ambient");
            String p = args[0].toLowerCase(Locale.ROOT);
            return out.stream().filter(s -> s.startsWith(p)).toList();
        }
        return List.of();
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
