package com.blake.portalplugin.commands;

import com.blake.portalplugin.BlastMinigameManager;
import com.blake.portalplugin.BlastTeam;
import com.blake.portalplugin.PortalPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SpawnProtectionCommand implements CommandExecutor, TabCompleter {

    private final PortalPlugin plugin;

    public SpawnProtectionCommand(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /spawnprotection.");
            return true;
        }

        if (args.length != 7) {
            sender.sendMessage("Usage: /spawnprotection <team> <x1> <y1> <z1> <x2> <y2> <z2>");
            return true;
        }

        BlastTeam team = BlastTeam.fromKey(args[0]);
        if (team == null) {
            sender.sendMessage("Unknown team. Use red, green, yellow, or blue.");
            return true;
        }

        double x1;
        double y1;
        double z1;
        double x2;
        double y2;
        double z2;

        try {
            x1 = Double.parseDouble(args[1]);
            y1 = Double.parseDouble(args[2]);
            z1 = Double.parseDouble(args[3]);
            x2 = Double.parseDouble(args[4]);
            y2 = Double.parseDouble(args[5]);
            z2 = Double.parseDouble(args[6]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("Coordinates must be numbers.");
            return true;
        }

        BlastMinigameManager manager = plugin.getBlastMinigameManager();
        if (manager == null) {
            sender.sendMessage("BLAST is not available on this server.");
            return true;
        }

        Location a = new Location(player.getWorld(), x1, y1, z1);
        Location b = new Location(player.getWorld(), x2, y2, z2);
        manager.setSpawnProtection(team, a, b);

        sender.sendMessage("Spawn protection set for " + team.getColor() + team.getKey().toUpperCase()
                + "§a in world " + player.getWorld().getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> teams = new ArrayList<>();
            for (BlastTeam team : BlastTeam.values()) {
                teams.add(team.getKey());
            }
            return teams;
        }
        return List.of();
    }
}
