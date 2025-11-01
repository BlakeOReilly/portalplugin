package com.blake.portalplugin;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class PlayerJoinListener implements Listener {
    private final GameStateManager stateManager;

    public PlayerJoinListener(GameStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stateManager.setGameState(player, GameState.HUB, null);
    }
}
