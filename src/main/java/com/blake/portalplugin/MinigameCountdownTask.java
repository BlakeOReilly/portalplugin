// MinigameCountdownTask.java
// NOTE: This file is provided because your MinigameQueueManager now constructs it as:
// new MinigameCountdownTask(this, seconds)
// If your existing MinigameCountdownTask already exists, replace it with this full version.
package com.blake.portalplugin;

import org.bukkit.scheduler.BukkitRunnable;

public class MinigameCountdownTask extends BukkitRunnable {

    private final MinigameQueueManager manager;
    private int time;

    public MinigameCountdownTask(MinigameQueueManager manager, int seconds) {
        this.manager = manager;
        this.time = Math.max(1, seconds);
    }

    @Override
    public void run() {

        if (manager == null || manager.isShutdown()) {
            if (manager != null) manager.clearCountdownRef();
            cancel();
            return;
        }

        // STOP if not enough players remain
        if (manager.getQueuedOnlineCount() < manager.getMinPlayers()) {
            manager.clearCountdownRef();
            cancel();
            return;
        }

        // FINISH
        if (time <= 0) {
            manager.clearCountdownRef();
            cancel();
            manager.startMinigameNow();
            return;
        }

        time--;
    }
}
