package com.blake.portalplugin;

import org.bukkit.entity.Player;

public class GameStateCommand {
    private GameStateManager manager;

    public void execute(Player p) {
        p.sendMessage("Your state: " + manager.getGameState(p));
    }
}