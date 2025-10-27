package com.blake.portalplugin;

import org.bukkit.entity.Player;

public class SignListener {
    private GameStateManager manager;

    public void onSignInteract(Player p) {
        if (manager.getGameState(p) != GameState.SPLEEF) return;
    }
}