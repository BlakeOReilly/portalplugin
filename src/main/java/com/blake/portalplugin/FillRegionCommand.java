package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.worldedit.BlockEditOperation;
import com.blake.portalplugin.worldedit.BlockEditTask;
import com.blake.portalplugin.worldedit.SelectionManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class FillRegionCommand implements CommandExecutor, TabCompleter {

    private final PortalPlugin plugin;
    private final SelectionManager selections;

    public FillRegionCommand(PortalPlugin plugin, SelectionManager selections) {
        this.plugin = plugin;
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

        if (args.length < 1) {
            p.sendMessage("Usage: /pfill <block> [onlyReplaceBlock]");
            p.sendMessage("Example: /pfill stone");
            p.sendMessage("Example: /pfill stone dirt  (only replaces dirt)");
            return true;
        }

        if (!selections.hasCompleteSelection(p.getUniqueId())) {
            p.sendMessage("Selection incomplete. Use /pos1 and /pos2 first.");
            return true;
        }

        if (!selections.sameWorld(p.getUniqueId())) {
            p.sendMessage("Pos1 and Pos2 must be in the same world.");
            return true;
        }

        Material setTo = parseBlockMaterial(args[0]);
        if (setTo == null) {
            p.sendMessage("Invalid block: " + args[0]);
            return true;
        }

        Material replaceOnly = null;
        if (args.length >= 2) {
            replaceOnly = parseBlockMaterial(args[1]);
            if (replaceOnly == null) {
                p.sendMessage("Invalid replace block: " + args[1]);
                return true;
            }
        }

        long volume = selections.getVolume(p.getUniqueId());

        long maxBlocks = plugin.getConfig().getLong("worldedit.max-blocks", 2_000_000L);
        int blocksPerTick = plugin.getConfig().getInt("worldedit.blocks-per-tick", 8000);

        if (volume <= 0) {
            p.sendMessage("Invalid selection.");
            return true;
        }

        if (volume > maxBlocks && !p.hasPermission("portalplugin.worldedit.bypasslimit")) {
            p.sendMessage("Selection too large (" + volume + " blocks). Limit is " + maxBlocks + ".");
            p.sendMessage("Increase worldedit.max-blocks in config.yml or grant portalplugin.worldedit.bypasslimit.");
            return true;
        }

        Location a = selections.getPos1(p.getUniqueId());
        Location b = selections.getPos2(p.getUniqueId());

        BlockEditTask.Bounds bounds = BlockEditTask.fromTwoLocations(a, b);

        p.sendMessage("Filling " + volume + " blocks with " + setTo
                + (replaceOnly != null ? " (only replacing " + replaceOnly + ")" : "") + "...");

        new BlockEditTask(
                plugin,
                p.getUniqueId(),
                a.getWorld(),
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                blocksPerTick,
                BlockEditOperation.FILL,
                setTo,
                replaceOnly
        ).runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    private Material parseBlockMaterial(String in) {
        if (in == null) return null;
        Material m = Material.matchMaterial(in);
        if (m == null) return null;
        if (!m.isBlock()) return null;
        return m;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 || args.length == 2) {
            String prefix = args[args.length - 1].toUpperCase();
            List<String> out = new ArrayList<>();
            for (Material m : Material.values()) {
                if (!m.isBlock()) continue;
                String name = m.name();
                if (name.startsWith(prefix)) out.add(name.toLowerCase());
            }
            return out;
        }
        return List.of();
    }
}