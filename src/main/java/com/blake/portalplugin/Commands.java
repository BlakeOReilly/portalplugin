package com.blake.portalplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands implements CommandExecutor {

    private final MainPlugin plugin;

    public Commands(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        switch (command.getName().toLowerCase()) {
            case "scoreboard":
                // Logic to handle scoreboard command
                player.sendMessage("Scoreboard command executed.");
                return true;
            default:
                return false;
        }
    }
}
