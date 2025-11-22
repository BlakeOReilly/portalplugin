package com.blake.portalplugin.commands;

import com.blake.portalplugin.WinLocationManager;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class SetWinLocationCommand implements CommandExecutor {

    private final WinLocationManager winLocationManager;

    public SetWinLocationCommand(WinLocationManager winLocationManager) {
        this.winLocationManager = winLocationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player may use this command.");
            return true;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /gamewinloc <x> <y> <z>");
            return true;
        }

        try {
            double x = Double.parseDouble(args[0]);
            double y = Double.parseDouble(args[1]);
            double z = Double.parseDouble(args[2]);

            Location loc = new Location(player.getWorld(), x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
            winLocationManager.setWinLocation(loc);

            player.sendMessage("§aWin location set to: §f" + x + " " + y + " " + z);
        } catch (NumberFormatException e) {
            player.sendMessage("§cCoordinates must be numbers.");
        }

        return true;
    }
}
