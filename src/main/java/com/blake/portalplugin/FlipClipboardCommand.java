package com.blake.portalplugin.commands;

import com.blake.portalplugin.worldedit.Clipboard;
import com.blake.portalplugin.worldedit.ClipboardManager;
import com.blake.portalplugin.worldedit.ClipboardManager.FlipAxis;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public class FlipClipboardCommand implements CommandExecutor, TabCompleter {

    private final ClipboardManager clipboardManager;

    public FlipClipboardCommand(ClipboardManager clipboardManager) {
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

        if (!clipboardManager.hasClipboard(p.getUniqueId())) {
            p.sendMessage("Clipboard empty. Use /pcopy first.");
            return true;
        }

        FlipAxis axis = FlipAxis.X;
        if (args.length >= 1) {
            String a0 = args[0].toLowerCase();
            if (a0.equals("z") || a0.equals("ns") || a0.equals("northsouth")) axis = FlipAxis.Z;
            else axis = FlipAxis.X;
        }

        Clipboard out = clipboardManager.flip(p.getUniqueId(), axis);
        if (out == null) {
            p.sendMessage("Clipboard empty. Use /pcopy first.");
            return true;
        }

        p.sendMessage("Flipped clipboard on " + axis.name() + " axis.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("x", "z");
        return List.of();
    }
}
