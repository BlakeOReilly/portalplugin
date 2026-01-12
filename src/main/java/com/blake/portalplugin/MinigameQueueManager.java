// MinigameQueueManager.java
package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class MinigameQueueManager {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;

    // Maintain join order
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();

    // Countdown reference
    private BukkitRunnable countdownTask = null;

    private boolean shutdown = false;

    public MinigameQueueManager(PortalPlugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
    }

    public void onConfigChanged() {
        // If this server is no longer a blast minigame hub, stop everything.
        if (!isBlastMinigameServer()) {
            clearQueueAndCountdown();
        }
    }

    public void shutdown() {
        shutdown = true;
        clearQueueAndCountdown();
    }

    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Called when a player joins the server (or when you want to re-evaluate them).
     */
    public void handleJoin(Player p) {
        if (shutdown) return;
        if (p == null) return;
        if (!isBlastMinigameServer()) return;

        BlastMinigameManager blast = plugin.getBlastMinigameManager();
        if (blast != null && blast.isInProgress()) {
            p.sendMessage("§e[BLAST] A match is already in progress. Please wait for it to finish.");
            return;
        }

        queue.add(p.getUniqueId());

        // Put player into QUEUING (safe helper exists on PortalPlugin)
        try {
            plugin.setPlayerQueuedStateSafe(p);
        } catch (Throwable ignored) {}

        p.sendMessage("§a[BLAST] You joined the queue. (" + getQueuedOnlineCount() + " queued)");

        // Start countdown if enough players
        startCountdownIfNeeded();
    }

    public void handleQuit(Player p) {
        if (p == null) return;

        queue.remove(p.getUniqueId());

        // If countdown running and we dropped below min players, cancel
        if (countdownTask != null && getQueuedOnlineCount() < getMinPlayers()) {
            broadcastToQueued("§c[BLAST] Countdown stopped – not enough players.");
            stopCountdown();
        }
    }

    /**
     * MinigameQueueCountdownTask expects this.
     * (Also used by other logic.)
     */
    public int size() {
        // Use online count so countdown doesn't keep going because of offline UUIDs.
        return getQueuedOnlineCount();
    }

    /**
     * MinigameQueueCountdownTask expects this.
     */
    public void stopCountdown() {
        if (countdownTask != null) {
            try { countdownTask.cancel(); } catch (Throwable ignored) {}
            countdownTask = null;
        }
    }

    /**
     * MinigameQueueCountdownTask expects this.
     */
    public void tryStartMinigameFromQueue() {
        // Clear countdown ref immediately so we can start a future countdown after this.
        stopCountdown();
        startMinigameNow();
    }

    /**
     * MinigameQueueCountdownTask expects this (it currently calls it).
     */
    public void broadcastToQueued(String msg) {
        if (msg == null) return;
        for (UUID id : queue) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    /**
     * Used by other countdown task variants and internal logic.
     */
    public int getQueuedOnlineCount() {
        int count = 0;
        for (UUID id : queue) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) count++;
        }
        return count;
    }

    /**
     * Used by other countdown task variants and internal logic.
     */
    public int getMinPlayers() {
        return plugin.getConfig().getInt("blast.queue.min-players", 2);
    }

    public int getCountdownSeconds() {
        return plugin.getConfig().getInt("blast.queue.countdown-seconds", 30);
    }

    /**
     * Some other countdown task variants expect this name.
     * Keep it for compatibility.
     */
    public void clearCountdownRef() {
        countdownTask = null;
    }

    /**
     * Some other countdown task variants expect this name.
     * Keep it for compatibility.
     */
    public void startMinigameNow() {
        if (shutdown) return;
        if (!isBlastMinigameServer()) return;

        BlastMinigameManager blast = plugin.getBlastMinigameManager();
        if (blast == null) return;

        if (blast.isInProgress()) {
            broadcastToQueued("§e[BLAST] A match is already in progress.");
            return;
        }

        List<Player> queuedPlayers = new ArrayList<>();
        for (UUID id : queue) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) queuedPlayers.add(p);
            if (queuedPlayers.size() >= BlastMinigameManager.MAX_PLAYERS) break;
        }

        if (queuedPlayers.size() < getMinPlayers()) {
            broadcastToQueued("§c[BLAST] Not enough players to start.");
            return;
        }

        List<UUID> consumed = blast.startFromQueue(queuedPlayers);

        // Remove consumed from queue
        if (consumed != null && !consumed.isEmpty()) {
            for (UUID id : consumed) queue.remove(id);
        }
    }

    private void startCountdownIfNeeded() {
        if (countdownTask != null) return;
        if (getQueuedOnlineCount() < getMinPlayers()) return;

        // Avoid starting countdown if BLAST already running
        BlastMinigameManager blast = plugin.getBlastMinigameManager();
        if (blast != null && blast.isInProgress()) return;

        broadcastToQueued("§a[BLAST] Enough players joined! Countdown started.");

        // Use the task class you posted (MinigameQueueCountdownTask)
        countdownTask = new MinigameQueueCountdownTask(this, getCountdownSeconds());
        countdownTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void clearQueueAndCountdown() {
        queue.clear();
        stopCountdown();
    }

    private boolean isBlastMinigameServer() {
        // Only run this queue logic when configured as a minigame hub AND server-minigame is blast
        if (!plugin.isMinigameHub()) return false;

        String mg = plugin.getServerMinigame();
        if (mg == null) return false;

        return mg.equalsIgnoreCase("blast");
    }
}
