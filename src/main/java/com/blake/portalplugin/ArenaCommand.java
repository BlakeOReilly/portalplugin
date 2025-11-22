package com.blake.portalplugin.commands;

import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class ArenaCommand implements CommandExecutor {

    private final ArenaManager arenaManager;

    public ArenaCommand(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {

        if (!cmd.getName().equalsIgnoreCase("arena"))
            return false;

        if (a.length < 2) {
            sender.sendMessage("Usage:");
            sender.sendMessage("/arena add <name>");
            sender.sendMessage("/arena spawn <name> <x> <y> <z>");
            sender.sendMessage("/arena save <name> <material> <x1> <y1> <z1> <x2> <y2> <z2>");
            sender.sendMessage("/arena reset <name>");
            return true;
        }

        String sub = a[0].toLowerCase();
        String arenaName = a[1].toLowerCase();

        /* =============================================================
           /arena add <name>
           ============================================================= */
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

        /* =============================================================
           /arena spawn <name> <x> <y> <z>
           ============================================================= */
        if (sub.equals("spawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players may add spawn points.");
                return true;
            }

            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage("Arena does not exist.");
                return true;
            }

            if (a.length != 5) {
                sender.sendMessage("Usage: /arena spawn <arena> <x> <y> <z>");
                return true;
            }

            Player p = (Player) sender;
            double x = Double.parseDouble(a[2]);
            double y = Double.parseDouble(a[3]);
            double z = Double.parseDouble(a[4]);
            Location loc = new Location(p.getWorld(), x, y, z);

            arenaManager.getArena(arenaName).addSpawn(loc);
            arenaManager.saveArenasToFile();
            sender.sendMessage("Added spawn to arena: " + arenaName);
            return true;
        }

        /* =============================================================
           /arena save <arena> <material> <x1 y1 z1 x2 y2 z2>
           ============================================================= */
        if (sub.equals("save")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players may run this command.");
                return true;
            }
            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage("Arena does not exist.");
                return true;
            }
            if (a.length != 9) {
                sender.sendMessage("Usage: /arena save <arena> <material> <x1 y1 z1 x2 y2 z2>");
                return true;
            }

            Material material = Material.matchMaterial(a[2]);
            if (material == null) {
                sender.sendMessage("Invalid material!");
                return true;
            }

            Player p = (Player) sender;
            Location c1 = new Location(p.getWorld(),
                    Double.parseDouble(a[3]),
                    Double.parseDouble(a[4]),
                    Double.parseDouble(a[5]));
            Location c2 = new Location(p.getWorld(),
                    Double.parseDouble(a[6]),
                    Double.parseDouble(a[7]),
                    Double.parseDouble(a[8]));

            Arena arena = arenaManager.getArena(arenaName);
            int count = arenaManager.saveArenaResetBlocks(arena, material, c1, c2);
            arenaManager.saveArenasToFile();

            sender.sendMessage("§aSaved §e" + count + "§a blocks of §b" + material.name()
                    + " §afor arena §6" + arenaName);
            return true;
        }

        /* =============================================================
           /arena reset <arena>
           ============================================================= */
        if (sub.equals("reset")) {

            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage("Arena does not exist.");
                return true;
            }

            int restored = arenaManager.resetArenaBlocks(arenaManager.getArena(arenaName));
            arenaManager.saveArenasToFile();

            sender.sendMessage("§aArena §6" + arenaName + "§a has been reset.");
            sender.sendMessage("§7Restored §e" + restored + " §7blocks.");
            return true;
        }

        sender.sendMessage("Unknown subcommand.");
        return true;
    }
}
