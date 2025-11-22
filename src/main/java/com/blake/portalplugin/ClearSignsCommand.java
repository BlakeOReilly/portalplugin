package com.blake.portalplugin.commands;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.queues.GameQueueManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ClearSignsCommand implements CommandExecutor {

    private final GameQueueManager queueManager;
    private final GameStateManager gameStateManager;
    private final JavaPlugin plugin;

    public ClearSignsCommand(GameQueueManager queueManager,
                             GameStateManager gameStateManager,
                             JavaPlugin plugin) {
        this.queueManager = queueManager;
        this.gameStateManager = gameStateManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // Only ADMIN players
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may run this command.");
            return true;
        }

        if (gameStateManager.getGameState(player) != GameState.ADMIN) {
            player.sendMessage("You must be in ADMIN gamestate to use /clearsigns.");
            return true;
        }

        int removed = 0;

        for (String entry : queueManager.getSignEntries()) {
            String[] parts = entry.split(",");
            if (parts.length != 5) continue;

            String worldName = parts[0];

            int x;
            int y;
            int z;

            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ex) {
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Block block = world.getBlockAt(x, y, z);

            if (block.hasMetadata("queue_sign")) {
                block.removeMetadata("queue_sign", plugin);
            }

            // Remove any type of sign
            if (block.getType().name().contains("SIGN")) {
                block.setType(org.bukkit.Material.AIR);
                removed++;
            }
        }

        // Reset the in-memory list
        queueManager.clearAllSigns();

        // Persist reset
        plugin.getConfig().set("queue-signs", queueManager.getSignEntries());
        plugin.saveConfig();

        player.sendMessage("§aCleared " + removed + " queue signs and reset all saved sign positions.");
        return true;
    }
}
