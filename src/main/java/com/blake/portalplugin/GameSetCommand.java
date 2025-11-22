package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GameSetCommand implements CommandExecutor, TabCompleter {

    private final PortalPlugin plugin;

    public GameSetCommand(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // Basic permission check
        if (!player.hasPermission("portal.gameset") && !player.isOp()) {
            player.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /gameset <gamemode>");
            player.sendMessage("§7Example: /gameset spleef");
            return true;
        }

        String gamemode = args[0].toLowerCase();

        // Persist active game to config.yml
        plugin.setActiveGame(gamemode);

        player.sendMessage("§aActive game for this server set to §e" + gamemode + "§a.");

        // Refresh all scoreboards so changes are visible immediately
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().refreshAll();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Known game types – currently only "spleef"
            if ("spleef".startsWith(partial)) {
                completions.add("spleef");
            }
        }

        return completions;
    }
}
