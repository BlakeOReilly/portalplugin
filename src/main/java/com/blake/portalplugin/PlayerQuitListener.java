package com.blake.portalplugin;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public class PlayerQuitListener implements Listener {
    private final GameStateManager stateManager;

    public PlayerQuitListener(GameStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stateManager.clearPlayer(player);
    }
}
