package com.blake.portalplugin.commands;

import com.blake.portalplugin.worldedit.SelectionManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Pos1Command implements CommandExecutor {

    private final SelectionManager selections;

    public Pos1Command(SelectionManager selections) {
        this.selections = selections;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!p.isOp() && !p.hasPermission("portalplugin.worldedit")) {
            p.sendMessage("No permission.");
            return true;
        }

        Location loc = resolveLocation(p, args);
        selections.setPos1(p.getUniqueId(), loc);

        p.sendMessage("Pos1 set to: " + loc.getWorld().getName() + " "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        return true;
    }

    private Location resolveLocation(Player p, String[] args) {
        if (args.length == 3) {
            try {
                int x = Integer.parseInt(args[0]);
                int y = Integer.parseInt(args[1]);
                int z = Integer.parseInt(args[2]);
                return new Location(p.getWorld(), x, y, z);
            } catch (NumberFormatException ignored) {}
        }

        Block target = p.getTargetBlockExact(200);
        if (target != null) return target.getLocation();

        return p.getLocation().getBlock().getLocation();
    }
}