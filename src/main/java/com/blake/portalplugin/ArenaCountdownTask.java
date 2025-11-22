package com.blake.portalplugin.arenas.tasks;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.arenas.Arena;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ArenaCountdownTask extends BukkitRunnable {

    private final Arena arena;
    private int time = 30;

    public ArenaCountdownTask(Arena arena) {
        this.arena = arena;
    }

    @Override
    public void run() {

        // STOP if arena is closed or game already started
        if (!arena.isInUse() || arena.hasStarted()) {
            arena.clearCountdown();
            cancel();
            return;
        }

        // STOP and reset if <2 players remain
        if (arena.getPlayers().size() < 2) {
            arena.broadcast("&cCountdown stopped – waiting for more players.");
            arena.clearCountdown();
            cancel();
            return;
        }

        // =========================
        // COUNTDOWN FINISHED
        // =========================
        if (time <= 0) {
            arena.broadcast("&aGame starting!");
            arena.setStarted(true);
            arena.clearCountdown();
            cancel();

            Plugin plugin = Bukkit.getPluginManager().getPlugin("PortalPlugin");
            if (plugin == null) {
                System.out.println("[Arena] FATAL: PortalPlugin NOT FOUND");
                return;
            }

            GameStateManager gsm = ((com.blake.portalplugin.PortalPlugin) plugin).getGameStateManager();

            for (Player p : arena.getOnlinePlayers()) {
                gsm.setGameState(p, GameState.SPLEEF);
            }
            return;
        }

        // =========================
        // SEND COUNTDOWN
        // =========================
        arena.broadcast("&eSpleef begins in &c" + time + "&e seconds!");
        time--;
    }
}
