package com.azpbmd.horror;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Analog-horror billboard that is not a vanilla mob. Custom pack texture + custom sounds.
 */
final class WatcherHaunt extends BukkitRunnable {
    private final HorrorDirectorPlugin plugin;
    private final Player player;
    private ItemDisplay body;
    private TextDisplay nametag;
    private int ticks;
    private int heartAcc;

    WatcherHaunt(HorrorDirectorPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        spawnAt(behind(18, 26));
        runTaskTimer(plugin, 4L, 4L);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }
        if (body == null || !body.isValid()) {
            spawnAt(behind(16, 24));
        }
        ticks++;
        Location here = body.getLocation();
        Location target = player.getLocation();
        double dist = here.distance(target);
        Vector look = player.getEyeLocation().getDirection().normalize();
        Vector to = here.toVector().subtract(player.getEyeLocation().toVector());
        boolean seen = to.lengthSquared() > 0.4 && to.normalize().dot(look) > 0.88;

        heartAcc++;
        if (heartAcc >= Math.max(4, (int) (dist))) {
            heartAcc = 0;
            float vol = (float) Math.min(1.0, 8.0 / Math.max(2.0, dist));
            custom(here, "heartbeat", vol, 0.55f);
            if (dist < 10) {
                custom(here, "step", 0.45f, 0.7f);
            }
        }

        if (dist < 2.4) {
            custom(player.getLocation(), "scream", 1.0f, 0.7f);
            custom(player.getLocation(), "glitch", 1.0f, 1.0f);
            Location spin = player.getLocation();
            spin.setYaw(spin.getYaw() + 180f);
            player.teleport(spin);
            plugin.scares().run(player, "glitch_error", ScareService.params());
            despawn();
            spawnAt(behind(20, 28));
            return;
        }

        double speed;
        Location dest;
        if (seen) {
            // Looked at it — it's suddenly behind you, closer (Myers).
            dest = behind(Math.max(3.5, dist * 0.45), Math.max(4.0, dist * 0.55));
            speed = 1.0;
            custom(here, "static", 0.35f, 1.2f);
        } else {
            Vector step = target.toVector().subtract(here.toVector());
            if (step.lengthSquared() < 0.01) {
                return;
            }
            speed = dist > 14 ? 0.55 : 0.32;
            dest = here.clone().add(step.normalize().multiply(speed));
            dest.setY(target.getY());
        }
        dest.setDirection(target.toVector().subtract(dest.toVector()));
        body.setInterpolationDelay(0);
        body.setInterpolationDuration(seen ? 1 : 5);
        body.teleport(dest);
        if (nametag != null && nametag.isValid()) {
            nametag.teleport(dest.clone().add(0, 2.6, 0));
        }
    }

    void nudge() {
        if (player.isOnline() && body != null && body.isValid()) {
            Location closer = behind(6, 10);
            body.teleport(closer);
            custom(closer, "voice", 0.8f, 0.6f);
        }
    }

    void eyesOnly() {
        if (!player.isOnline()) {
            return;
        }
        Location at = behind(11, 16).add(0, 1.6, 0);
        ItemDisplay eyes = player.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(modeled("eyes"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setViewRange(1.2f);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf(),
                    new Vector3f(2.2f, 1.1f, 0.05f),
                    new Quaternionf()));
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (eyes.isValid()) {
                eyes.remove();
            }
        }, 35L);
        custom(at, "whisper", 0.9f, 0.5f);
    }

    void despawn() {
        if (body != null && body.isValid()) {
            body.remove();
        }
        if (nametag != null && nametag.isValid()) {
            nametag.remove();
        }
        body = null;
        nametag = null;
    }

    @Override
    public synchronized void cancel() {
        despawn();
        super.cancel();
    }

    private void spawnAt(Location loc) {
        despawn();
        loc.setY(player.getLocation().getY());
        Vector toPlayer = player.getEyeLocation().toVector().subtract(loc.toVector());
        if (toPlayer.lengthSquared() > 0.01) {
            loc.setDirection(toPlayer);
        }
        body = player.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(modeled("watcher"));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setViewRange(2.0f);
            d.setGlowColorOverride(Color.WHITE);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 1.8f, 0f),
                    new Quaternionf(),
                    new Vector3f(2.8f, 6.5f, 0.08f),
                    new Quaternionf()));
        });
        nametag = player.getWorld().spawn(loc.clone().add(0, 2.6, 0), TextDisplay.class, t -> {
            t.text(net.kyori.adventure.text.Component.text(player.getName())
                    .color(net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setSeeThrough(true);
            t.setShadowed(true);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(1.5f);
        });
        custom(loc, "rumble", 0.5f, 0.5f);
    }

    private Location behind(double min, double max) {
        Vector back = player.getLocation().getDirection().multiply(-1).setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(1, 0, 0);
        }
        double dist = min + ThreadLocalRandom.current().nextDouble() * Math.max(0.1, max - min);
        double side = (ThreadLocalRandom.current().nextDouble() - 0.5) * 6;
        Vector right = back.clone().normalize().crossProduct(new Vector(0, 1, 0));
        return player.getLocation().clone()
                .add(back.normalize().multiply(dist))
                .add(right.normalize().multiply(side));
    }

    private ItemStack modeled(String path) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("horror", path));
        stack.setItemMeta(meta);
        return stack;
    }

    private void custom(Location at, String key, float vol, float pitch) {
        player.playSound(at, "horror:" + key, vol, pitch);
    }
}
