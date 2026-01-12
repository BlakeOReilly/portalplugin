package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.queues.GameQueue;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public class HubTypeCommand implements CommandExecutor, TabCompleter {

    private final PortalPlugin plugin;

    public HubTypeCommand(PortalPlugin plugin) {
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
            sender.sendMessage("§cUsage: /hubtype <arena|minigame>");
            return true;
        }

        String type = args[0] == null ? "" : args[0].trim().toLowerCase();
        if (!type.equals("arena") && !type.equals("minigame")) {
            sender.sendMessage("§cInvalid hub type. Use: arena, minigame");
            return true;
        }

        plugin.setHubType(type);

        if (type.equals("arena")) {

            // When switching back to ARENA hub, ensure online players are not left queued.
            for (Player p : Bukkit.getOnlinePlayers()) {
                Arena inArena = plugin.getArenaManager().getArenaPlayerIsIn(p);
                if (inArena != null) continue;
                plugin.getQueueManager().removePlayerFromAllQueues(p);
            }

            sender.sendMessage("§aHub type set to: §fARENA§a.");

        } else {
            // MINIGAME hub: auto-queue players as they join.
            String game = plugin.getServerMinigame();
            if (game == null || game.isBlank() || game.equalsIgnoreCase("none")) {
                sender.sendMessage("§eHub type set to §fMINIGAME§e, but server-minigame is not set.");
                sender.sendMessage("§eSet it with: §f/setminigame <game>");
            } else {
                GameQueue q = plugin.getQueueManager().getOrCreateQueue(game);

                // Queue current online players (not in an arena)
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Arena inArena = plugin.getArenaManager().getArenaPlayerIsIn(p);
                    if (inArena != null) continue;

                    plugin.getQueueManager().removePlayerFromAllQueues(p);
                    q.addPlayer(p);
                    plugin.setPlayerQueuedStateSafe(p);
                }

                plugin.getQueueManager().handlePlayerQueued(game);

                sender.sendMessage("§aHub type set to: §fMINIGAME§a. Auto-queue enabled for: §f" + game + "§a.");
            }
        }

        plugin.updateEffectiveActiveGameAndRegistry();

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAdminPerm(sender)) return List.of();

        if (args.length == 1) {
            String prefix = args[0] == null ? "" : args[0].toLowerCase();
            return List.of("arena", "minigame").stream().filter(s -> s.startsWith(prefix)).toList();
        }

        return List.of();
    }
}
