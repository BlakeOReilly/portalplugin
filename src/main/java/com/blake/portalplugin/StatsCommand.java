package com.blake.portalplugin.commands;

import com.blake.portalplugin.stats.PlayerStats;
import com.blake.portalplugin.stats.StatsGUI;
import com.blake.portalplugin.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class StatsCommand implements CommandExecutor {

    private final StatsManager statsManager;

    public StatsCommand(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /stats.");
            return true;
        }

        // Load stats async, then open GUI on main thread
        statsManager.loadStatsAsync(player.getUniqueId(), stats -> {
            if (stats == null) {
                player.sendMessage("§cError loading stats.");
                return;
            }
            StatsGUI.open(player, stats);
        });

        return true;
    }
}
