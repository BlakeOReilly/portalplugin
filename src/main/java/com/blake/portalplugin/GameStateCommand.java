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
            sender.sendMessage("Unknown or incomplete command, see below for error");
            return false;
        }
        // Add logic to handle the command based on args
        // Example: if (args[0].equalsIgnoreCase("start")) { ... }
        sender.sendMessage("Command executed successfully!");
        return true;
    }
}
