package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.queues.GameQueue;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SetMinigameCommand implements CommandExecutor, TabCompleter {

    private final PortalPlugin plugin;

    public SetMinigameCommand(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean hasAdminPerm(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("portalplugin.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!hasAdminPerm(sender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /setminigame <game>");
            return true;
        }

        String game = args[0] == null ? "" : args[0].trim().toLowerCase();
        if (game.isBlank()) {
            sender.sendMessage("§cGame cannot be blank.");
            return true;
        }

        plugin.setServerMinigame(game);

        // Ensure queue exists
        GameQueue q = plugin.getQueueManager().getOrCreateQueue(game);

        // If this server is currently in MINIGAME hub mode, re-queue online players (not in an arena)
        if (plugin.isMinigameHub()) {

            // Remove online players from any existing queues first (avoids being stuck in old minigame queue)
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getQueueManager().removePlayerFromAllQueues(p);
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                Arena inArena = plugin.getArenaManager().getArenaPlayerIsIn(p);
                if (inArena != null) continue;

                q.addPlayer(p);
                plugin.setPlayerQueuedStateSafe(p);
            }

            // Try to allocate/merge into arenas immediately
            plugin.getQueueManager().handlePlayerQueued(game);
        }

        sender.sendMessage("§aServer minigame set to: §f" + game + "§a.");

        // Update registry "active-game" representation
        plugin.updateEffectiveActiveGameAndRegistry();

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAdminPerm(sender)) return List.of();

        if (args.length == 1) {
            String prefix = args[0] == null ? "" : args[0].toLowerCase();
            List<String> out = new ArrayList<>();

            // Suggest existing queues
            for (String q : plugin.getQueueManager().getQueueNames()) {
                if (q.startsWith(prefix)) out.add(q);
            }

            // If nothing exists yet, provide a few common examples
            if (out.isEmpty()) {
                for (String s : List.of("blast", "spleef", "pvp", "sumo")) {
                    if (s.startsWith(prefix)) out.add(s);
                }
            }

            return out;
        }

        return List.of();
    }
}
