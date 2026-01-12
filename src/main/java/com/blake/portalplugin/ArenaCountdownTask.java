package com.blake.portalplugin.arenas.tasks;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.PortalPlugin;
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

        String gameNameDisplay = arena.getAssignedGameDisplayName();

        // =========================
        // COUNTDOWN FINISHED
        // =========================
        if (time <= 0) {

            arena.broadcast("&aGame starting!");
            arena.setStarted(true);

            // Clear our task reference on the arena, then stop the runnable
            arena.clearCountdown();
            cancel();

            Plugin plugin = Bukkit.getPluginManager().getPlugin("PortalPlugin");
            if (!(plugin instanceof PortalPlugin portal)) {
                System.out.println("[Arena] FATAL: PortalPlugin NOT FOUND (or wrong type)");
                return;
            }

            GameStateManager gsm = portal.getGameStateManager();

            String rawAssigned = arena.getAssignedGame();
            String normalized = normalizeGameKey(rawAssigned);

            GameState targetState = resolveTargetState(normalized);

            if (targetState == GameState.ARENA) {
                // If we failed to resolve a real game state, keep players in ARENA and log it.
                portal.getLogger().warning("[PortalPlugin] Arena '" + arena.getName()
                        + "' assignedGame raw='" + rawAssigned
                        + "' normalized='" + normalized
                        + "' -> no matching GameState found. Leaving players in ARENA.");
            } else {
                portal.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                        + "' starting assignedGame='" + normalized
                        + "' -> GameState=" + targetState.name());
            }

            for (Player p : arena.getOnlinePlayers()) {
                gsm.setGameState(p, targetState);
            }

            return;
        }

        // =========================
        // SEND COUNTDOWN
        // =========================
        arena.broadcast("&e" + gameNameDisplay + " begins in &c" + time + "&e seconds!");
        time--;
    }

    /**
     * Normalizes arena assigned game keys so queues like "Spleef-1" still resolve.
     * Examples:
     *  - "Spleef"   -> "spleef"
     *  - "spleef-1" -> "spleef"
     *  - "Pvp-2"    -> "pvp"
     *  - " sumo "   -> "sumo"
     */
    private String normalizeGameKey(String gameKey) {
        if (gameKey == null) return null;

        String s = gameKey.trim().toLowerCase();

        // Common pattern: "pvp-1", "spleef-2"
        int dash = s.indexOf('-');
        if (dash > 0) s = s.substring(0, dash);

        // Remove trailing digits if any: "sumo1" -> "sumo"
        while (!s.isEmpty() && Character.isDigit(s.charAt(s.length() - 1))) {
            s = s.substring(0, s.length() - 1);
        }

        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private GameState resolveTargetState(String gameKey) {
        if (gameKey == null || gameKey.isBlank()) return GameState.ARENA;

        // First: if your enum supports aliases (recommended)
        try {
            GameState byFromString = GameState.fromString(gameKey);
            if (byFromString != null) return byFromString;
        } catch (Throwable ignored) {
            // If fromString ever changes/gets removed, we still have fallback logic below.
        }

        // Fallback: direct enum match
        try {
            return GameState.valueOf(gameKey.trim().toUpperCase());
        } catch (Exception ignored) {}

        // Final fallback: explicit mapping for known games
        return switch (gameKey.trim().toLowerCase()) {
            case "pvp" -> GameState.PVP;
            case "spleef" -> GameState.SPLEEF;
            case "sumo" -> GameState.SUMO;
            default -> GameState.ARENA;
        };
    }
}
