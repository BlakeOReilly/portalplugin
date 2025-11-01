package com.blake.portalplugin;

import org.bukkit.entity.Player;

public class GameStateManager {
    public GameState getGameState(Player player) {
        // Implement logic to get the game state for the player
        return GameState.HUB; // Placeholder
    }

    public void setGameState(Player player, GameState state, Object data) {
        // Implement logic to set the game state for the player
    }

    public void clearPlayer(Player player) {
        // Implement logic to clear the player's game state
    }
}