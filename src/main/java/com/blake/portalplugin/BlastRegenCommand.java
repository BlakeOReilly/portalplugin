package com.blake.portalplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class BlastRegenCommand implements CommandExecutor {

    private final PortalPlugin plugin;

    public BlastRegenCommand(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("portalplugin.blast.regen")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        BlastMinigameManager mgr = plugin.getBlastMinigameManager();
        if (mgr == null) {
            sender.sendMessage("§c[BLAST] BlastMinigameManager not available.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            mgr.reloadMaps();
            sender.sendMessage("§a[BLAST] Reloaded blast-maps.yml");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            List<String> maps = mgr.getMapStore().listMapNames();
            sender.sendMessage("§e[BLAST] Maps: " + (maps.isEmpty() ? "(none)" : String.join(", ", maps)));
            return true;
        }

        String mapName = (args.length >= 1 ? args[0] : plugin.getConfig().getString("blast.active-map", ""));
        mgr.regenerateMap(mapName, sender);
        return true;
    }
}
