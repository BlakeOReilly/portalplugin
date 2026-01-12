package com.blake.portalplugin;

import org.bukkit.scheduler.BukkitRunnable;

public class MinigameQueueCountdownTask extends BukkitRunnable {

    private final MinigameQueueManager manager;
    private int seconds;

    public MinigameQueueCountdownTask(MinigameQueueManager manager, int seconds) {
        this.manager = manager;
        this.seconds = Math.max(1, seconds);
    }

    @Override
    public void run() {
        if (manager == null) {
            cancel();
            return;
        }

        if (seconds <= 0) {
            manager.broadcastToQueued("§a[Queue] Starting BLAST!");
            manager.tryStartMinigameFromQueue();
            cancel();
            return;
        }

        // If less than 2 players remain, stop
        if (manager.size() < 2) {
            manager.broadcastToQueued("§c[Queue] Countdown stopped – waiting for more players.");
            manager.stopCountdown();
            cancel();
            return;
        }

        manager.broadcastToQueued("§e[Queue] BLAST begins in §c" + seconds + "§e seconds!");
        seconds--;
    }
}
