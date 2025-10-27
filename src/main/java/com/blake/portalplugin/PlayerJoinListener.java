package com.blake.portalplugin;

import org.bukkit.entity.Player;

public class PlayerJoinListener {
    private GameStateManager stateManager;

    public void onPlayerJoin(Player player) {
        stateManager.setGameState(player, GameState.HUB, null);
    }
}