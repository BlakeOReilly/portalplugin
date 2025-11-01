package com.blake.portalplugin;

import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;

public class GameStateManager {
    private final ScoreboardManager scoreboardManager;

    public GameStateManager(ScoreboardManager scoreboardManager) {
        this.scoreboardManager = scoreboardManager;
    }

    // Add methods for getGameState, setGameState, clearPlayer, etc.
    public GameState getGameState(Player player) {
        // Implementation here
        return GameState.HUB; // Placeholder
    }

    public void setGameState(Player player, GameState state, Object obj) {
        // Implementation here
    }

    public void clearPlayer(Player player) {
        // Implementation here
    }
}
