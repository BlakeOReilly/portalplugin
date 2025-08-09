package com.blake.portalplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class PlayerJoinListener implements Listener {
    private final HubStatsPlugin  plugin;
    private final GameStateManager stateManager;

    public PlayerJoinListener(HubStatsPlugin plugin, GameStateManager stateManager) {
        this.plugin       = plugin;
        this.stateManager = stateManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // Put every new player into the HUB state
        stateManager.setGameState(player, GameState.HUB, null);
    }
}
