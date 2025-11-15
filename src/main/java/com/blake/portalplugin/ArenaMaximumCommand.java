package com.blake.portalplugin.commands;

import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ArenaMaximumCommand implements CommandExecutor {

    private final ArenaManager arenaManager;

    public ArenaMaximumCommand(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length != 2) {
            sender.sendMessage("Usage: /arenamaximum <arena> <maxPlayers>");
            return true;
        }

        String arenaName = args[0].toLowerCase();

        if (!arenaManager.arenaExists(arenaName)) {
            sender.sendMessage("Arena does not exist.");
            return true;
        }

        int maxPlayers;
        try {
            maxPlayers = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Max players must be a number.");
            return true;
        }

        Arena arena = arenaManager.getArena(arenaName);
        arena.setMaxPlayers(maxPlayers);
        arenaManager.saveArenasToFile();

        sender.sendMessage("Arena '" + arenaName + "' max players set to: " + maxPlayers);
        return true;
    }
}
