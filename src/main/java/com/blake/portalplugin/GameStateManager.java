package com.blake.portalplugin;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

public class GameStateManager {
    private Map<Player, GameState> playerStates = new HashMap<>();

    public GameState getGameState(Player player) {
        return playerStates.getOrDefault(player, GameState.DEFAULT);
    }

    public void setGameState(Player player, GameState state, Object additionalData) {
        playerStates.put(player, state);
    }

    public void clearPlayer(Player player) {
        playerStates.remove(player);
    }

    public void removePlayerFromGame(Player player) {
        // Implementation to remove player from game
    }
}
