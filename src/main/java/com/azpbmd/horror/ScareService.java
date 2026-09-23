package com.azpbmd.horror;

import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

final class ScareService {
    private static final String[] FAKE_NAMES = {
            "Herobrine", "Notch", "null", "Dinnerbone", "Entity", "You", "Admin", "Help",
            "wilder0p", "jeb_", "Server", "Minecraft", "The_Entity", "00000000"
    };
    private static final String[] WHISPERS = {
            "behind you",
            "i see you",
            "stop running",
            "you shouldn't be here",
            "don't turn around",
            "i've always been here",
            "they're in the walls",
            "say my name",
            "you left the door open",
            "i have your things",
            "look up",
            "it knows your name",
            "this is not survival",
            "the others can't hear you",
            "i took something"
    };

    private final HorrorDirectorPlugin plugin;
    private final Map<String, BiFunction<Player, JsonObject, String>> types = new LinkedHashMap<>();
    private final Map<java.util.UUID, List<Entity>> temps = new ConcurrentHashMap<>();

    ScareService(HorrorDirectorPlugin plugin) {
        this.plugin = plugin;
        add("whisper", this::whisper);
        add("heartbeat", this::heartbeat);
        add("cave", this::cave);
        add("footsteps", this::footsteps);
        add("thunder", this::thunder);
        add("scream", this::scream);
        add("rumble", this::rumble);
        add("breath", this::breath);
        add("darkness", this::darkness);
        add("blindness", this::blindness);
        add("nausea", this::nausea);
        add("freeze", this::freeze);
        add("burn", this::burn);
        add("drown", this::drown);
        add("elder", this::elder);
        add("creeper_hiss", this::creeperHiss);
        add("warden", this::warden);
        add("wither", this::wither);
        add("enderman", this::enderman);
        add("sculk", this::sculk);
        add("souls", this::souls);
        add("blood", this::blood);
        add("lightning", this::lightning);
        add("lightning_fake", this::lightningFake);
        add("look_behind", this::lookBehind);
        add("spin", this::spin);
        add("twitch", this::twitch);
        add("possess", this::possess);
        add("tp_near", this::tpNear);
        add("tp_up", this::tpUp);
        add("fake_player", this::fakePlayer);
        add("clone", this::clone);
        add("herobrine", this::herobrine);
        add("watch_far", this::watchFar);
        add("watch_near", this::watchNear);
        add("it_follows", this::itFollows);
        add("hangup", this::hangup);
        add("knock", this::knock);
        add("footprints", this::footprints);
        add("letter", this::letter);
        add("knowing", this::knowing);
        add("isolate", this::isolate);
        add("trophy_steal", this::trophySteal);
        add("trophy_return", this::trophyReturn);
        add("stalk", this::stalk);
        add("watcher_close", this::watcherClose);
        add("eyes", this::eyesHaunt);
        add("glitch_error", this::glitchError);
        add("glitch_blocks", this::glitchBlocks);
        add("glitch_static", this::glitchStatic);
        add("glitch_name", this::glitchName);
        add("creaking", this::creaking);
        add("stalker", this::stalker);
        add("horde", this::horde);
        add("phantom", this::phantom);
        add("pumpkin", this::pumpkin);
        add("inventory_shuffle", this::inventoryShuffle);
        add("drop_held", this::dropHeld);
        add("steal_hotbar", this::stealHotbar);
        add("junk", this::junk);
        add("damage", this::damage);
        add("kill", this::kill);
        add("fake_death", this::fakeDeath);
        add("fake_kick", this::fakeKick);
        add("fake_ban", this::fakeBan);
        add("crash_kick", this::crashKick);
        add("send_lobby", this::sendLobby);
        add("title", this::title);
        add("actionbar", this::actionbar);
        add("fake_chat", this::fakeChat);
        add("fake_tell", this::fakeTell);
        add("fake_advancement", this::fakeAdvancement);
        add("scale_tiny", this::scaleTiny);
        add("scale_huge", this::scaleHuge);
        add("gravity", this::gravity);
        add("bury_fake", this::buryFake);
        add("wall_eyes", this::wallEyes);
        add("time_flicker", this::timeFlicker);
        add("xp_drain", this::xpDrain);
        add("hunger", this::hunger);
        add("clear_inv", this::clearInv);
        add("levitation", this::levitation);
        add("glow", this::glow);
        add("infested", this::infested);
        add("silence", this::silence);
        add("random", this::random);
        add("chaos", this::chaos);
        add("reset", this::reset);
    }

    List<String> types() {
        return List.copyOf(types.keySet());
    }

    static JsonObject params() {
        return new JsonObject();
    }

    String run(Player player, String type, JsonObject params) {
        if (player == null || !player.isOnline()) {
            return "offline";
        }
        BiFunction<Player, JsonObject, String> fn = types.get(type == null ? "" : type.toLowerCase());
        if (fn == null) {
            return "unknown type " + type;
        }
        try {
            return fn.apply(player, params == null ? params() : params);
        } catch (Exception e) {
            plugin.getLogger().warning("scare " + type + " failed: " + e.getMessage());
            return "error " + e.getClass().getSimpleName();
        }
    }

    void cleanup(Player player) {
        List<Entity> list = temps.remove(player.getUniqueId());
        if (list == null) {
            return;
        }
        for (Entity e : list) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
    }

    String randomMild(Player player) {
        String[] mild = {"whisper", "heartbeat", "cave", "footsteps", "thunder", "breath",
                "souls", "sculk", "twitch", "creeper_hiss", "actionbar", "fake_tell"};
        return run(player, mild[rng().nextInt(mild.length)], params());
    }

    String randomMean(Player player) {
        String[] mean = {"clone", "herobrine", "elder", "look_behind", "fake_chat",
                "inventory_shuffle", "drop_held", "fake_death", "fake_kick", "bury_fake", "eyes",
                "possess", "creaking", "horde", "blood", "scale_tiny", "tp_near", "fake_ban"};
        return run(player, mean[rng().nextInt(mean.length)], params());
    }

    private void add(String name, BiFunction<Player, JsonObject, String> fn) {
        types.put(name, fn);
    }

    private String whisper(Player p, JsonObject o) {
        play(p, behind(p, 4, 9), Sound.AMBIENT_CAVE, 1.0f, 0.5f);
        playCustom(p, "horror:whisper", 0.9f, 0.7f);
        String msg = str(o, "message", WHISPERS[rng().nextInt(WHISPERS.length)]);
        p.sendActionBar(Component.text(msg).color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        return "whisper";
    }

    private String heartbeat(Player p, JsonObject o) {
        int times = i(o, "times", 6);
        for (int n = 0; n < times; n++) {
            int delay = n * 18;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.6f);
                    playCustom(p, "horror:heartbeat", 1.0f, 1.0f);
                }
            }, delay);
        }
        return "heartbeat";
    }

    private String cave(Player p, JsonObject o) {
        p.playSound(behind(p, 6, 16), Sound.AMBIENT_CAVE, 1.0f, 0.4f);
        p.playSound(p.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8f, 0.7f);
        return "cave";
    }

    private String footsteps(Player p, JsonObject o) {
        Location start = behind(p, 6, 10);
        for (int n = 0; n < 8; n++) {
            int step = n;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                Location at = start.clone().add(p.getLocation().toVector().subtract(start.toVector())
                        .normalize().multiply(step * 0.7));
                p.playSound(at, Sound.BLOCK_STONE_STEP, 1.0f, 0.8f);
                p.spawnParticle(Particle.SMOKE, at.add(0, 0.1, 0), 3, 0.15, 0.05, 0.15, 0.0);
            }, n * 7L);
        }
        return "footsteps";
    }

    private String thunder(Player p, JsonObject o) {
        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.55f);
        return "thunder";
    }

    private String scream(Player p, JsonObject o) {
        playCustom(p, "horror:scream", 1.0f, 0.8f);
        p.playSound(p.getLocation(), Sound.ENTITY_GHAST_HURT, 1.0f, 0.4f);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.8f, 0.5f);
        p.showTitle(Title.title(
                Component.text(" ").color(NamedTextColor.DARK_RED),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(180), Duration.ofMillis(80))));
        return "scream";
    }

    private String rumble(Player p, JsonObject o) {
        playCustom(p, "horror:rumble", 1.0f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.35f, 0.3f);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.25f, 0.4f);
        return "rumble";
    }

    private String breath(Player p, JsonObject o) {
        playCustom(p, "horror:breath", 1.0f, 0.8f);
        p.playSound(behind(p, 1, 3), Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.6f);
        return "breath";
    }

    private String darkness(Player p, JsonObject o) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * i(o, "seconds", 12), 0, false, false, false));
        return "darkness";
    }

    private String blindness(Player p, JsonObject o) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * i(o, "seconds", 6), 0, false, false, false));
        return "blindness";
    }

    private String nausea(Player p, JsonObject o) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * i(o, "seconds", 8), 2, false, false, false));
        return "nausea";
    }

    private String freeze(Player p, JsonObject o) {
        p.setFreezeTicks(20 * i(o, "seconds", 8));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * i(o, "seconds", 8), 4, false, false, false));
        return "freeze";
    }

    private String burn(Player p, JsonObject o) {
        p.setFireTicks(20 * i(o, "seconds", 4));
        return "burn";
    }

    private String drown(Player p, JsonObject o) {
        p.setRemainingAir(0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_DROWN, 1.0f, 1.0f);
        return "drown";
    }

    private String elder(Player p, JsonObject o) {
        p.showElderGuardian();
        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
        return "elder";
    }

    private String creeperHiss(Player p, JsonObject o) {
        Location at = behind(p, 2, 4);
        p.playSound(at, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.7f);
            }
        }, 30L);
        return "creeper_hiss";
    }

    private String warden(Player p, JsonObject o) {
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 0.8f, 0.8f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 8, 0, false, false, false));
        return "warden";
    }

    private String wither(Player p, JsonObject o) {
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);
        return "wither";
    }

    private String enderman(Player p, JsonObject o) {
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.6f);
        p.playSound(behind(p, 3, 8), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
        p.spawnParticle(Particle.PORTAL, p.getEyeLocation(), 40, 0.6, 0.6, 0.6, 0.4);
        return "enderman";
    }

    private String sculk(Player p, JsonObject o) {
        p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 0.8f);
        p.playSound(behind(p, 4, 10), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 0.5f);
        p.spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 18, 1.2, 0.5, 1.2, 0.02);
        return "sculk";
    }

    private String souls(Player p, JsonObject o) {
        p.spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 20, 1.4, 0.7, 1.4, 0.02);
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.5f);
        return "souls";
    }

    private String blood(Player p, JsonObject o) {
        p.spawnParticle(Particle.DAMAGE_INDICATOR, p.getEyeLocation(), 18, 0.5, 0.4, 0.5, 0.1);
        p.spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.2,
                Material.REDSTONE_BLOCK.createBlockData());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
        p.damage(i(o, "amount", 2));
        return "blood";
    }

    private String lightning(Player p, JsonObject o) {
        p.getWorld().strikeLightning(p.getLocation());
        return "lightning";
    }

    private String lightningFake(Player p, JsonObject o) {
        p.getWorld().strikeLightningEffect(p.getLocation().add(offset(-6, 6), 0, offset(-6, 6)));
        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        return "lightning_fake";
    }

    private String lookBehind(Player p, JsonObject o) {
        Location loc = p.getLocation();
        loc.setYaw(loc.getYaw() + 180f);
        p.teleport(loc);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.4f);
        return "look_behind";
    }

    private String spin(Player p, JsonObject o) {
        Location loc = p.getLocation();
        for (int n = 1; n <= 8; n++) {
            int step = n;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                Location l = p.getLocation();
                l.setYaw(l.getYaw() + 45f);
                p.teleport(l);
            }, step * 2L);
        }
        return "spin";
    }

    private String twitch(Player p, JsonObject o) {
        Location loc = p.getLocation();
        loc.setYaw(loc.getYaw() + (float) offset(-25, 25));
        loc.setPitch(Math.max(-90, Math.min(90, loc.getPitch() + (float) offset(-15, 15))));
        p.teleport(loc);
        return "twitch";
    }

    private String possess(Player p, JsonObject o) {
        int n = i(o, "times", 12);
        for (int k = 0; k < n; k++) {
            int step = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    twitch(p, params());
                }
            }, step * 3L);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false, false));
        return "possess";
    }

    private String tpNear(Player p, JsonObject o) {
        Location loc = p.getLocation().add(offset(-6, 6), 0, offset(-6, 6));
        loc.setY(p.getWorld().getHighestBlockYAt(loc) + 1);
        if (!p.getWorld().getWorldBorder().isInside(loc)) {
            return "skipped";
        }
        p.teleport(loc);
        p.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        return "tp_near";
    }

    private String tpUp(Player p, JsonObject o) {
        p.teleport(p.getLocation().add(0, i(o, "blocks", 8), 0));
        return "tp_up";
    }

    private String fakePlayer(Player p, JsonObject o) {
        return spawnFigure(p, str(o, "name", FAKE_NAMES[rng().nextInt(FAKE_NAMES.length)]), false, 7, 14);
    }

    private String clone(Player p, JsonObject o) {
        return spawnFigure(p, p.getName(), true, 3, 6);
    }

    private String herobrine(Player p, JsonObject o) {
        String result = spawnFigure(p, "Herobrine", true, 10, 18);
        p.sendActionBar(Component.text("Herobrine joined the game").color(NamedTextColor.YELLOW));
        return result;
    }

    private String watchFar(Player p, JsonObject o) {
        return spawnFigure(p, "", true, 18, 28);
    }

    private String watchNear(Player p, JsonObject o) {
        return spawnFigure(p, "", true, 5, 9);
    }

    private String itFollows(Player p, JsonObject o) {
        Location start = behind(p, 16, 22);
        start.setY(p.getLocation().getY());
        Vector toPlayer = p.getEyeLocation().toVector().subtract(start.toVector());
        if (toPlayer.lengthSquared() > 0.01) {
            start.setDirection(toPlayer);
        }
        org.bukkit.entity.Entity figure;
        try {
            figure = p.getWorld().spawn(start, org.bukkit.entity.Mannequin.class, m -> {
                m.setInvulnerable(true);
                m.setCollidable(false);
                m.setGravity(false);
                m.setAI(false);
                m.setSilent(true);
                m.setDescription(Component.empty());
                m.setImmovable(true);
                m.setCustomNameVisible(false);
                try {
                    m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            figure = p.getWorld().spawn(start, org.bukkit.entity.ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setGravity(false);
                s.setSilent(true);
                s.setInvulnerable(true);
            });
        }
        track(p, figure);
        org.bukkit.entity.Entity moving = figure;
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!p.isOnline() || !moving.isValid() || ticks > 160) {
                    if (moving.isValid()) {
                        moving.remove();
                    }
                    cancel();
                    return;
                }
                Location here = moving.getLocation();
                Location target = p.getLocation();
                Vector step = target.toVector().subtract(here.toVector());
                if (step.lengthSquared() < 9) {
                    moving.remove();
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.4f);
                    scaresLookBehind(p);
                    cancel();
                    return;
                }
                Vector look = p.getEyeLocation().getDirection().normalize();
                Vector to = here.toVector().subtract(p.getEyeLocation().toVector());
                boolean seen = to.lengthSquared() > 0.2 && to.normalize().dot(look) > 0.92;
                double speed = seen ? 0.12 : 0.38;
                Location next = here.add(step.normalize().multiply(speed));
                next.setDirection(target.toVector().subtract(next.toVector()));
                moving.teleport(next);
            }
        }.runTaskTimer(plugin, 8L, 4L);
        return "it_follows";
    }

    private void scaresLookBehind(Player p) {
        Location loc = p.getLocation();
        loc.setYaw(loc.getYaw() + 180f);
        p.teleport(loc);
    }

    private String hangup(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().hangup(p, plugin.engine().hunt(p), bool(o, "speak", false));
    }

    private String knock(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().knock(p);
    }

    private String footprints(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().footprints(p, 8, 16);
    }

    private String letter(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().letter(p, plugin.engine().hunt(p));
    }

    private String knowing(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().knowing(p, plugin.engine().hunt(p), true);
    }

    private String isolate(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().isolate(p, plugin.engine().hunt(p));
    }

    private String trophySteal(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().stealTrophy(p, plugin.engine().hunt(p));
    }

    private String trophyReturn(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().returnTrophy(p, plugin.engine().hunt(p));
    }

    private String stalk(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().beat(p);
    }

    private String watcherClose(Player p, JsonObject o) {
        if (plugin.engine() == null) {
            return "no-engine";
        }
        plugin.engine().watcher(p).nudge();
        return "watcher_close";
    }

    private String eyesHaunt(Player p, JsonObject o) {
        if (plugin.engine() == null) {
            return "no-engine";
        }
        plugin.engine().watcher(p).eyesOnly();
        return "eyes";
    }

    private String glitchError(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().glitchError(p);
    }

    private String glitchBlocks(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().glitchBlocks(p);
    }

    private String glitchStatic(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().glitchStatic(p);
    }

    private String glitchName(Player p, JsonObject o) {
        return plugin.engine() == null ? "no-engine" : plugin.engine().glitchName(p);
    }

    private String creaking(Player p, JsonObject o) {
        return spawnMob(p, entity("creaking"), 8, 16, 160);
    }

    private String stalker(Player p, JsonObject o) {
        EntityType[] pool = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ENDERMAN, EntityType.SPIDER,
                EntityType.HUSK, EntityType.STRAY};
        return spawnMob(p, pool[rng().nextInt(pool.length)], 10, 22, 200);
    }

    private String horde(Player p, JsonObject o) {
        int n = i(o, "count", 8);
        for (int k = 0; k < n; k++) {
            spawnMob(p, EntityType.ZOMBIE, 6, 14, 240);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.6f);
        return "horde " + n;
    }

    private String phantom(Player p, JsonObject o) {
        p.setStatistic(org.bukkit.Statistic.TIME_SINCE_REST, 80_000);
        return spawnMob(p, EntityType.PHANTOM, 8, 16, 260);
    }

    private String pumpkin(Player p, JsonObject o) {
        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        pumpkin.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.BINDING_CURSE, 1);
        pumpkin.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.VANISHING_CURSE, 1);
        p.getInventory().setHelmet(pumpkin);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);
        return "pumpkin";
    }

    private String inventoryShuffle(Player p, JsonObject o) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            int j = rng().nextInt(contents.length);
            ItemStack tmp = contents[i];
            contents[i] = contents[j];
            contents[j] = tmp;
        }
        inv.setStorageContents(contents);
        return "inventory_shuffle";
    }

    private String dropHeld(Player p, JsonObject o) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return "empty";
        }
        p.getInventory().setItemInMainHand(null);
        p.getWorld().dropItemNaturally(p.getLocation(), held);
        return "drop_held";
    }

    private String stealHotbar(Player p, JsonObject o) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) {
                p.getWorld().dropItemNaturally(p.getLocation(), it);
                inv.setItem(i, null);
            }
        }
        return "steal_hotbar";
    }

    private String junk(Player p, JsonObject o) {
        Material[] junk = {Material.ROTTEN_FLESH, Material.BONE, Material.SPIDER_EYE, Material.GUNPOWDER,
                Material.PLAYER_HEAD, Material.SOUL_SAND, Material.DEAD_BUSH};
        p.getInventory().addItem(new ItemStack(junk[rng().nextInt(junk.length)], 1 + rng().nextInt(4)));
        return "junk";
    }

    private String damage(Player p, JsonObject o) {
        p.damage(Math.max(1, i(o, "amount", 4)));
        p.sendHurtAnimation(0f);
        return "damage";
    }

    private String kill(Player p, JsonObject o) {
        p.setHealth(0);
        return "kill";
    }

    private String fakeDeath(Player p, JsonObject o) {
        p.showTitle(Title.title(
                Component.text("You died!").color(NamedTextColor.RED),
                Component.text("Score: " + rng().nextInt(400)).color(NamedTextColor.WHITE),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);
        p.setHealth(Math.max(0.5, p.getHealth() * 0.25));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 4, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 8, 0, false, false, false));
        return "fake_death";
    }

    private String fakeKick(Player p, JsonObject o) {
        p.showTitle(Title.title(
                Component.text("Disconnected").color(NamedTextColor.WHITE),
                Component.text("Internal Exception: java.net.SocketException").color(NamedTextColor.RED),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(6), Duration.ofMillis(200))));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 6, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 6, 9, false, false, false));
        p.setFreezeTicks(20 * 6);
        return "fake_kick";
    }

    private String fakeBan(Player p, JsonObject o) {
        String reason = str(o, "reason", "We know what you did.");
        p.showTitle(Title.title(
                Component.text("You have been banned").color(NamedTextColor.RED),
                Component.text(reason).color(NamedTextColor.DARK_RED),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(10), Duration.ofSeconds(1))));
        p.sendMessage(Component.text("You have been permanently banned from this server.")
                .color(NamedTextColor.RED));
        p.sendMessage(Component.text("Reason: " + reason).color(NamedTextColor.GRAY));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 10, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 10, 9, false, false, false));
        p.setFreezeTicks(20 * 10);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.4f);
        return "fake_ban";
    }

    private String crashKick(Player p, JsonObject o) {
        p.kick(Component.text("Internal Exception: io.netty.handler.codec.DecoderException: "
                + "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"this.name\" is null"));
        return "crash_kick";
    }

    private String sendLobby(Player p, JsonObject o) {
        String dest = str(o, "server", "lobby");
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            out.writeUTF("Connect");
            out.writeUTF(dest);
            p.sendPluginMessage(plugin, "BungeeCord", buf.toByteArray());
            return "send " + dest;
        } catch (Exception e) {
            return "send-failed";
        }
    }

    private String title(Player p, JsonObject o) {
        p.showTitle(Title.title(
                Component.text(str(o, "title", "BEHIND YOU")).color(NamedTextColor.DARK_RED),
                Component.text(str(o, "subtitle", "")).color(NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(300))));
        return "title";
    }

    private String actionbar(Player p, JsonObject o) {
        p.sendActionBar(Component.text(str(o, "message", WHISPERS[rng().nextInt(WHISPERS.length)]))
                .color(NamedTextColor.DARK_RED).decorate(TextDecoration.ITALIC));
        return "actionbar";
    }

    private String fakeChat(Player p, JsonObject o) {
        String from = str(o, "from", fakeName(p));
        String msg = str(o, "message", WHISPERS[rng().nextInt(WHISPERS.length)]);
        Component line = Component.text("<" + from + "> ").color(NamedTextColor.WHITE)
                .append(Component.text(msg));
        if (bool(o, "all", false)) {
            Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(line));
        } else {
            p.sendMessage(line);
        }
        return "fake_chat " + from;
    }

    private String fakeTell(Player p, JsonObject o) {
        String from = str(o, "from", fakeName(p));
        String msg = str(o, "message", WHISPERS[rng().nextInt(WHISPERS.length)]);
        p.sendMessage(Component.text(from + " whispers to you: ").color(NamedTextColor.GRAY)
                .append(Component.text(msg).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)));
        p.playSound(p.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.5f, 0.4f);
        return "fake_tell " + from;
    }

    private String fakeAdvancement(Player p, JsonObject o) {
        String name = str(o, "name", "The End?");
        p.sendMessage(Component.text(p.getName() + " has made the advancement [")
                .color(NamedTextColor.WHITE)
                .append(Component.text(name).color(NamedTextColor.GREEN).decorate(TextDecoration.ITALIC))
                .append(Component.text("]")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 0.7f);
        return "fake_advancement";
    }

    private String scaleTiny(Player p, JsonObject o) {
        setScale(p, d(o, "scale", 0.35));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                setScale(p, 1.0);
            }
        }, 20L * i(o, "seconds", 20));
        return "scale_tiny";
    }

    private String scaleHuge(Player p, JsonObject o) {
        setScale(p, d(o, "scale", 2.4));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                setScale(p, 1.0);
            }
        }, 20L * i(o, "seconds", 12));
        return "scale_huge";
    }

    private String gravity(Player p, JsonObject o) {
        Attribute attr = Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("gravity"));
        if (attr != null && p.getAttribute(attr) != null) {
            p.getAttribute(attr).setBaseValue(d(o, "value", 0.02));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getAttribute(attr) != null) {
                    p.getAttribute(attr).setBaseValue(0.08);
                }
            }, 20L * i(o, "seconds", 8));
        }
        return "gravity";
    }

    private String buryFake(Player p, JsonObject o) {
        Location feet = p.getLocation();
        Material mat = Material.COBWEB;
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location b = feet.clone().add(x, y, z);
                    p.sendBlockChange(b, mat.createBlockData());
                    Location restore = b.clone();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) {
                            p.sendBlockChange(restore, restore.getBlock().getBlockData());
                        }
                    }, 20L * 8);
                }
            }
        }
        p.playSound(feet, Sound.BLOCK_WOOL_PLACE, 1.0f, 0.5f);
        return "bury_fake";
    }

    private String wallEyes(Player p, JsonObject o) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location at = eye.clone().add(dir.multiply(5));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(p);
            head.setItemMeta(meta);
        }
        p.sendBlockChange(at, Material.PLAYER_HEAD.createBlockData());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.sendBlockChange(at, at.getBlock().getBlockData());
            }
        }, 40L);
        p.playSound(at, Sound.ENTITY_ENDERMAN_STARE, 0.7f, 0.4f);
        return "eyes";
    }

    private String timeFlicker(Player p, JsonObject o) {
        for (int n = 0; n < 10; n++) {
            int step = n;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                p.setPlayerTime(step % 2 == 0 ? 18000L : 1000L, false);
            }, step * 6L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.setPlayerTime(18000L, false);
            }
        }, 80L);
        return "time_flicker";
    }

    private String xpDrain(Player p, JsonObject o) {
        p.setExp(0);
        p.setLevel(Math.max(0, p.getLevel() - i(o, "levels", 3)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 0.3f);
        return "xp_drain";
    }

    private String hunger(Player p, JsonObject o) {
        p.setFoodLevel(Math.max(0, i(o, "food", 2)));
        p.setSaturation(0);
        return "hunger";
    }

    private String clearInv(Player p, JsonObject o) {
        p.getInventory().clear();
        return "clear_inv";
    }

    private String levitation(Player p, JsonObject o) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * i(o, "seconds", 4),
                i(o, "amp", 2), false, false, false));
        return "levitation";
    }

    private String glow(Player p, JsonObject o) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * i(o, "seconds", 15), 0, false, false, false));
        return "glow";
    }

    private String infested(Player p, JsonObject o) {
        PotionEffectType type = PotionEffectType.getByName("INFESTED");
        if (type != null) {
            p.addPotionEffect(new PotionEffect(type, 20 * 20, 0, false, true, true));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_SILVERFISH_AMBIENT, 1.0f, 0.7f);
        return "infested";
    }

    private String silence(Player p, JsonObject o) {
        p.stopAllSounds();
        return "silence";
    }

    private String random(Player p, JsonObject o) {
        return randomMean(p);
    }

    private String chaos(Player p, JsonObject o) {
        randomMean(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                randomMean(p);
            }
        }, 30L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                randomMild(p);
            }
        }, 70L);
        return "chaos";
    }

    private String reset(Player p, JsonObject o) {
        p.clearActivePotionEffects();
        p.setFreezeTicks(0);
        p.setFireTicks(0);
        p.setPlayerTime(18000L, false);
        setScale(p, 1.0);
        Attribute gravity = Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("gravity"));
        if (gravity != null && p.getAttribute(gravity) != null) {
            p.getAttribute(gravity).setBaseValue(0.08);
        }
        cleanup(p);
        return "reset";
    }

    private String spawnFigure(Player p, String name, boolean useSkin, double min, double max) {
        Location at = behind(p, min, max);
        at.setY(p.getLocation().getY());
        Vector toPlayer = p.getEyeLocation().toVector().subtract(at.toVector());
        if (toPlayer.lengthSquared() > 0.01) {
            at.setDirection(toPlayer);
        }
        try {
            Mannequin mannequin = p.getWorld().spawn(at, Mannequin.class, m -> {
                m.setInvulnerable(true);
                m.setCollidable(false);
                m.setGravity(false);
                m.setAI(false);
                m.setSilent(true);
                m.setDescription(Component.empty());
                m.setImmovable(true);
                if (name == null || name.isBlank()) {
                    m.setCustomNameVisible(false);
                } else {
                    m.customName(Component.text(name).color(NamedTextColor.DARK_RED));
                    m.setCustomNameVisible(true);
                }
                if (useSkin) {
                    try {
                        m.setProfile(ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
                    } catch (Exception ignored) {
                    }
                }
            });
            track(p, mannequin);
            vanishWhenLookedAt(p, mannequin, 90L);
            p.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.25f, 0.4f);
            return "figure " + name;
        } catch (Exception e) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(p);
                head.setItemMeta(meta);
            }
            var stand = p.getWorld().spawn(at, org.bukkit.entity.ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setGravity(false);
                s.setSilent(true);
                s.setInvulnerable(true);
                s.setCollidable(false);
                s.getEquipment().setHelmet(head);
                s.customName(Component.text(name));
                s.setCustomNameVisible(true);
            });
            track(p, stand);
            vanishWhenLookedAt(p, stand, 80L);
            return "stand " + name;
        }
    }

    private String spawnMob(Player p, EntityType type, double min, double max, long life) {
        if (type == null) {
            return "no-type";
        }
        Location at = offsetLoc(p.getLocation(), min, max);
        at.setY(p.getLocation().getY());
        if (!at.getWorld().getWorldBorder().isInside(at)) {
            return "skipped";
        }
        Entity spawned = at.getWorld().spawnEntity(at, type);
        if (spawned instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(true);
            living.setCanPickupItems(false);
        }
        track(p, spawned);
        vanishWhenLookedAt(p, spawned, life);
        return type.name().toLowerCase();
    }

    private void vanishWhenLookedAt(Player p, Entity entity, long lifeTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                entity.remove();
            }
        }, lifeTicks);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || !entity.isValid()) {
                    cancel();
                    return;
                }
                Vector look = p.getEyeLocation().getDirection().normalize();
                Vector to = entity.getLocation().toVector().subtract(p.getEyeLocation().toVector());
                if (to.lengthSquared() < 0.4) {
                    entity.remove();
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.4f);
                    cancel();
                    return;
                }
                if (to.normalize().dot(look) > 0.92) {
                    entity.remove();
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.5f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 4L);
    }

    private void track(Player p, Entity entity) {
        temps.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(entity);
    }

    private void setScale(Player p, double value) {
        Attribute scale = Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("scale"));
        if (scale != null && p.getAttribute(scale) != null) {
            p.getAttribute(scale).setBaseValue(value);
        }
    }

    private EntityType entity(String key) {
        try {
            return Registry.ENTITY_TYPE.get(org.bukkit.NamespacedKey.minecraft(key));
        } catch (Exception e) {
            return null;
        }
    }

    private void play(Player p, Location at, Sound sound, float vol, float pitch) {
        p.playSound(at, sound, vol, pitch);
    }

    private void playCustom(Player p, String namespaced, float vol, float pitch) {
        String[] parts = namespaced.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key(parts[0], parts[1]), Source.MASTER, vol, pitch));
    }

    private Location behind(Player p, double min, double max) {
        Vector back = p.getLocation().getDirection().multiply(-1).setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(1, 0, 0);
        }
        double dist = min + rng().nextDouble() * Math.max(0.1, max - min);
        return p.getLocation().clone().add(back.normalize().multiply(dist));
    }

    private Location offsetLoc(Location from, double min, double max) {
        double dist = min + rng().nextDouble() * Math.max(0.1, max - min);
        double yaw = rng().nextDouble() * Math.PI * 2;
        return from.clone().add(Math.cos(yaw) * dist, 0, Math.sin(yaw) * dist);
    }

    private String fakeName(Player p) {
        if (rng().nextBoolean()) {
            return p.getName();
        }
        Player other = Bukkit.getOnlinePlayers().stream()
                .filter(pl -> !pl.getUniqueId().equals(p.getUniqueId()))
                .findAny()
                .orElse(null);
        if (other != null && rng().nextBoolean()) {
            return other.getName();
        }
        return FAKE_NAMES[rng().nextInt(FAKE_NAMES.length)];
    }

    private static ThreadLocalRandom rng() {
        return ThreadLocalRandom.current();
    }

    private static String str(JsonObject o, String k, String d) {
        return o != null && o.has(k) ? o.get(k).getAsString() : d;
    }

    private static int i(JsonObject o, String k, int d) {
        try {
            return o != null && o.has(k) ? o.get(k).getAsInt() : d;
        } catch (Exception e) {
            return d;
        }
    }

    private static double d(JsonObject o, String k, double def) {
        try {
            return o != null && o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean bool(JsonObject o, String k, boolean d) {
        return o != null && o.has(k) ? o.get(k).getAsBoolean() : d;
    }

    private static double offset(double min, double max) {
        return min + rng().nextDouble() * (max - min);
    }
}
