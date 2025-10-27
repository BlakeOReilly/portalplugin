package com.blake.portalplugin;

import org.bukkit.entity.Player;

public class SpleefBlockListener {
    private GameStateManager manager;

    public void onBlockBreak(Player p) {
        if (manager.getGameState(p) != GameState.SPLEEF) return;
    }
}