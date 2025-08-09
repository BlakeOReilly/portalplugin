package com.blake.portalplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GameStateCommand implements CommandExecutor {
    private final GameStateManager manager;

    public GameStateCommand(GameStateManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] args) {
        if (!(s instanceof Player p)) return true;
        if (args.length == 0) {
            p.sendMessage("Your state: " + manager.getGameState(p));
            return true;
        }
        try {
            GameState newState = GameState.valueOf(args[0].toUpperCase());
            manager.setGameState(p, newState, null);
            p.sendMessage("State set to " + newState);
        } catch (IllegalArgumentException e) {
            p.sendMessage("Unknown state. Use HUB, QUEUING, GAMEPREP, SPLEEF, ADMIN.");
        }
        return true;
    }
}
