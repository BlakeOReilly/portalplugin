package com.blake.portalplugin;

import org.bukkit.entity.Player;

public class GameStateListener {
    private GameStateManager manager;

    public void onPlayerEvent(Player p) {
        GameState state = manager.getGameState(p);
        // Additional logic here
    }
}