package com.blake.portalplugin.commands;

import com.blake.portalplugin.HubSpawnManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final HubSpawnManager hubSpawnManager;

    public SetSpawnCommand(HubSpawnManager hubSpawnManager) {
        this.hubSpawnManager = hubSpawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /setspawn.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 3) {
            sender.sendMessage("Usage: /setspawn <x> <y> <z>");
            return true;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(args[0]);
            y = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("Coordinates must be numbers.");
            return true;
        }

        if (player.getWorld() == null) {
            sender.sendMessage("Your world is not loaded.");
            return true;
        }

        Location loc = new Location(
                player.getWorld(),
                x,
                y,
                z,
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
        );

        hubSpawnManager.setHubSpawn(loc);
        sender.sendMessage("Hub spawn set to " +
                String.format("(%.2f, %.2f, %.2f) in world %s",
                        x, y, z, player.getWorld().getName()));

        return true;
    }
}
