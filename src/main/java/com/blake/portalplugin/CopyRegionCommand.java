package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.worldedit.BlockCopyTask;
import com.blake.portalplugin.worldedit.ClipboardManager;
import com.blake.portalplugin.worldedit.SelectionManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CopyRegionCommand implements CommandExecutor {

    private final PortalPlugin plugin;
    private final SelectionManager selections;
    private final ClipboardManager clipboardManager;

    public CopyRegionCommand(PortalPlugin plugin, SelectionManager selections, ClipboardManager clipboardManager) {
        this.plugin = plugin;
        this.selections = selections;
        this.clipboardManager = clipboardManager;
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

        if (!selections.hasCompleteSelection(p.getUniqueId())) {
            p.sendMessage("Selection incomplete. Use /pos1 and /pos2 first.");
            return true;
        }

        if (!selections.sameWorld(p.getUniqueId())) {
            p.sendMessage("Pos1 and Pos2 must be in the same world.");
            return true;
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

        BlockCopyTask.Bounds bounds = BlockCopyTask.fromTwoLocations(a, b);

        p.sendMessage("Copying " + volume + " blocks to clipboard...");

        new BlockCopyTask(
                plugin,
                p.getUniqueId(),
                a.getWorld(),
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                blocksPerTick,
                clipboardManager
        ).runTaskTimer(plugin, 1L, 1L);

        return true;
    }
}
