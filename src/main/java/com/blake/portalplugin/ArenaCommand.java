package com.blake.portalplugin.commands;

import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class ArenaCommand implements CommandExecutor {

    private final ArenaManager arenaManager;

    public ArenaCommand(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!cmd.getName().equalsIgnoreCase("arena")) return false;

        if (args.length < 2) {
            sender.sendMessage("Usage:");
            sender.sendMessage("/arena add <name>");
            sender.sendMessage("/arena spawn <name> <x> <y> <z>");
            return true;
        }

        String sub = args[0].toLowerCase();
        String arenaName = args[1].toLowerCase();

        // /arena add <name>
        if (sub.equals("add")) {
            if (arenaManager.arenaExists(arenaName)) {
                sender.sendMessage("Arena already exists.");
                return true;
            }

            arenaManager.createArena(arenaName);
            arenaManager.saveArenasToFile();
            sender.sendMessage("Created arena: " + arenaName);
            return true;
        }

        // /arena spawn <name> <x> <y> <z>
        if (sub.equals("spawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can add spawn points.");
                return true;
            }

            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage("Arena does not exist.");
                return true;
            }

            if (args.length != 5) {
                sender.sendMessage("Usage: /arena spawn <name> <x> <y> <z>");
                return true;
            }

            Player player = (Player) sender;

            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);

            Location loc = new Location(player.getWorld(), x, y, z);

            Arena arena = arenaManager.getArena(arenaName);
            arena.addSpawn(loc);

            arenaManager.saveArenasToFile();
            sender.sendMessage("Added spawn point to arena: " + arenaName);
            return true;
        }

        sender.sendMessage("Unknown subcommand.");
        return true;
    }
}
