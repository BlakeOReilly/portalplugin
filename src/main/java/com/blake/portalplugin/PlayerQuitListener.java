package com.blake.portalplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final GameQueueManager queueManager;
    private final GameStateManager stateManager;

    public PlayerQuitListener(GameQueueManager queueManager,
                               GameStateManager stateManager) {
        this.queueManager = queueManager;
        this.stateManager = stateManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        queueManager.removePlayer(p);
        stateManager.clearPlayer(p);
    }
}
