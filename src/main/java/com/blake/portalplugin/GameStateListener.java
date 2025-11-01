package com.blake.portalplugin;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class GameStateListener implements Listener {
    private final GameStateManager manager;

    public GameStateListener(GameStateManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameState state = manager.getGameState(player);
        // Additional logic based on the game state
    }
}
