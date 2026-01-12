package com.blake.portalplugin.commands;

import com.blake.portalplugin.worldedit.Clipboard;
import com.blake.portalplugin.worldedit.ClipboardManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public class RotateClipboardCommand implements CommandExecutor, TabCompleter {

    private final ClipboardManager clipboardManager;

    public RotateClipboardCommand(ClipboardManager clipboardManager) {
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

        int deg = 90;
        if (args.length >= 1) {
            try {
                deg = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        Clipboard out = clipboardManager.rotate(p.getUniqueId(), deg);
        if (out == null) {
            p.sendMessage("Clipboard empty. Use /pcopy first.");
            return true;
        }

        p.sendMessage("Rotated clipboard by " + (deg % 360) + " degrees. New size: "
                + out.getWidth() + "x" + out.getHeight() + "x" + out.getLength() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("90", "180", "270");
        return List.of();
    }
}
