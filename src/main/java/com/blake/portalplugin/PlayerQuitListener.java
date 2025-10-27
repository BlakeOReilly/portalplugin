package com.blake.portalplugin;

import org.bukkit.entity.Player;

public class PlayerQuitListener {
    private GameQueueManager queueManager;
    private GameStateManager stateManager;

    public void onPlayerQuit(Player p) {
        queueManager.removePlayer(p);
        stateManager.clearPlayer(p);
    }
}