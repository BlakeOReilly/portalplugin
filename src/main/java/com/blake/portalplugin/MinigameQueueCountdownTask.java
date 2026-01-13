package com.blake.portalplugin;

import org.bukkit.scheduler.BukkitRunnable;

public class MinigameQueueCountdownTask extends BukkitRunnable {

    private final MinigameQueueManager manager;
    private int seconds;
    private boolean paused;

    public MinigameQueueCountdownTask(MinigameQueueManager manager, int seconds) {
        this.manager = manager;
        this.seconds = Math.max(1, seconds);
    }

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = Math.max(1, seconds);
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    @Override
    public void run() {
        if (manager == null) {
            cancel();
            return;
        }

        // If less than min players remain, stop
        if (manager.size() < manager.getMinPlayers()) {
            manager.broadcastToQueued("§c[Queue] Countdown stopped – waiting for more players.");
            manager.stopCountdown();
            cancel();
            return;
        }

        if (paused) {
            return;
        }

        if (seconds <= 0) {
            manager.broadcastToQueued("§a[Queue] Starting BLAST!");
            manager.tryStartMinigameFromQueue();
            cancel();
            return;
        }

        manager.broadcastToQueued("§e[Queue] BLAST begins in §c" + seconds + "§e seconds!");
        seconds--;
    }
}
