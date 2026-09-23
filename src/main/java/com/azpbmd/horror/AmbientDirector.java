package com.azpbmd.horror;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

final class AmbientDirector {
    private final HorrorDirectorPlugin plugin;
    private final ScareService scares;
    private BukkitTask task;

    AmbientDirector(HorrorDirectorPlugin plugin, ScareService scares) {
        this.plugin = plugin;
        this.scares = scares;
    }

    void start() {
        schedule(40L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void schedule(long delay) {
        task = Bukkit.getScheduler().runTaskLater(plugin, this::tick, Math.max(20L, delay));
    }

    private void tick() {
        try {
            if (plugin.ambientEnabled() && plugin.engine() != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.engine().beat(player);
                }
            }
        } finally {
            schedule(40L);
        }
    }
}
