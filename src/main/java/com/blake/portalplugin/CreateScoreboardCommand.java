package com.blake.portalplugin.commands;

import com.blake.portalplugin.holograms.HologramManager;
import com.blake.portalplugin.stats.PlayerStats;
import com.blake.portalplugin.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreateScoreboardCommand implements CommandExecutor {

    private final StatsManager statsManager;
    private final HologramManager hologramManager;

    public CreateScoreboardCommand(StatsManager statsManager, HologramManager hologramManager) {
        this.statsManager = statsManager;
        this.hologramManager = hologramManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /createscoreboard <gamemode> wins");
            return true;
        }

        String gamemode = args[0].toLowerCase();
        String type = args[1].toLowerCase();

        if (!type.equals("wins")) {
            player.sendMessage("§cOnly 'wins' leaderboard is supported right now.");
            return true;
        }

        player.sendMessage("§7Loading leaderboard...");

        // ASYNC database query
        statsManager.getTopWins(gamemode, 10, (top) -> {
            Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("PortalPlugin"),
                    () -> {

                List<String> lines = new ArrayList<>();
                lines.add("§eTop Wins – §b" + gamemode);

                int position = 1;
                for (PlayerStats s : top) {

                    UUID uuid = UUID.fromString(s.getUuid());
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    if (name == null) name = uuid.toString().substring(0, 8);

                    lines.add("§6" + position + ". §f" + name + " §7- §a" + s.getWins());
                    position++;
                }

                if (top.isEmpty()) {
                    lines.add("§7No data found.");
                }

                String id = gamemode + "_wins";
                var loc = player.getLocation().add(0, 2, 0);

                // Correct method call
                hologramManager.createLeaderboard(id, loc, lines);

                player.sendMessage("§aLeaderboard created!");
            });
        });

        return true;
    }
}
