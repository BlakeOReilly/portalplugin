package com.blake.portalplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class GameStateCommand implements CommandExecutor {
    private final MainPlugin plugin;

    public GameStateCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Command logic here
        return true;
    }
}