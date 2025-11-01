package com.blake.portalplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class GamestateCommand implements CommandExecutor {
    private final JavaPlugin plugin;

    public GamestateCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /gamestate <state>");
            return true;
        }
        String state = args[0];
        // Logic to handle game state
        sender.sendMessage("Game state set to: " + state);
        return true;
    }
}
