package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DiamondSpawnRangeCommand implements CommandExecutor {

    private final PortalPlugin plugin;

    public DiamondSpawnRangeCommand(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /diamondspawnrange <minY> <maxY>");
            return true;
        }

        int minY;
        int maxY;
        try {
            minY = Integer.parseInt(args[0]);
            maxY = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("Y range must be whole numbers.");
            return true;
        }

        if (minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        plugin.getConfig().set("blast.diamond-spawn.min-y", minY);
        plugin.getConfig().set("blast.diamond-spawn.max-y", maxY);
        plugin.saveConfig();

        sender.sendMessage("Diamond spawn Y range set to " + minY + " through " + maxY + ".");
        return true;
    }
}
