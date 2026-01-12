package com.blake.portalplugin;

import org.bukkit.scheduler.BukkitRunnable;

public class BlastGameTimerTask extends BukkitRunnable {

    private final BlastMinigameManager manager;

    public BlastGameTimerTask(BlastMinigameManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        if (manager == null || !manager.isInProgress()) {
            cancel();
            return;
        }

        manager.tickSecond();
    }
}
