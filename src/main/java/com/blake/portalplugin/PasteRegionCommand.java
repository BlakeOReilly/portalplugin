package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.worldedit.BlockPasteTask;
import com.blake.portalplugin.worldedit.Clipboard;
import com.blake.portalplugin.worldedit.ClipboardManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public class PasteRegionCommand implements CommandExecutor, TabCompleter {

    private final PortalPlugin plugin;
    private final ClipboardManager clipboardManager;

    public PasteRegionCommand(PortalPlugin plugin, ClipboardManager clipboardManager) {
        this.plugin = plugin;
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

        Clipboard cb = clipboardManager.getClipboard(p.getUniqueId());
        if (cb == null) {
            p.sendMessage("Clipboard empty. Use /pcopy first.");
            return true;
        }

        boolean pasteAir = true;

        int i = 0;
        if (args.length >= 1) {
            String a0 = args[0].toLowerCase();
            if (a0.equals("-a") || a0.equals("noair") || a0.equals("na")) {
                pasteAir = false;
                i = 1;
            }
        }

        Location origin = resolvePasteOrigin(p, args, i);

        long volume = cb.getVolume();
        long maxBlocks = plugin.getConfig().getLong("worldedit.max-blocks", 2_000_000L);
        int blocksPerTick = plugin.getConfig().getInt("worldedit.blocks-per-tick", 8000);

        if (volume > maxBlocks && !p.hasPermission("portalplugin.worldedit.bypasslimit")) {
            p.sendMessage("Clipboard too large (" + volume + " blocks). Limit is " + maxBlocks + ".");
            p.sendMessage("Increase worldedit.max-blocks in config.yml or grant portalplugin.worldedit.bypasslimit.");
            return true;
        }

        p.sendMessage("Pasting " + volume + " blocks at "
                + origin.getBlockX() + " " + origin.getBlockY() + " " + origin.getBlockZ()
                + (pasteAir ? "" : " (no air)") + "...");

        new BlockPasteTask(
                plugin,
                p.getUniqueId(),
                origin.getWorld(),
                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                cb,
                blocksPerTick,
                pasteAir
        ).runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    private Location resolvePasteOrigin(Player p, String[] args, int startIndex) {

        // /ppaste [-a] x y z
        if (args.length - startIndex >= 3) {
            try {
                int x = Integer.parseInt(args[startIndex]);
                int y = Integer.parseInt(args[startIndex + 1]);
                int z = Integer.parseInt(args[startIndex + 2]);
                return new Location(p.getWorld(), x, y, z);
            } catch (NumberFormatException ignored) {}
        }

        // else: paste on top of targeted block, else at player's block
        Block target = p.getTargetBlockExact(200);
        if (target != null) {
            Location base = target.getLocation();
            return base.add(0, 1, 0); // paste on top of looked-at block
        }

        return p.getLocation().getBlock().getLocation();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("-a", "noair");
        }
        return List.of();
    }
}
