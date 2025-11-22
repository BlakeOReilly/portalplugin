package com.blake.portalplugin.listeners;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

public class BlockBreakListener implements Listener {

    private final GameStateManager gameStateManager;
    private final Plugin plugin;

    public BlockBreakListener(GameStateManager gsm, Plugin plugin) {
        this.gameStateManager = gsm;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {

        Block block = event.getBlock();
        Material type = block.getType();
        var player = event.getPlayer();

        if (gameStateManager.getGameState(player) == GameState.SPLEEF
                && isWool(type)) {

            event.setInstaBreak(true);
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {

        Block block = event.getBlock();
        var player = event.getPlayer();
        Material type = block.getType();

        // Allow SPLEEF to break wool
        if (gameStateManager.getGameState(player) == GameState.SPLEEF
                && isWool(type)) {

            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }

        // NEW — allow ADMIN to break ANY block normally
        if (gameStateManager.getGameState(player) == GameState.ADMIN) {
            return;
        }

        // Restore for all other states
        var data = block.getBlockData();
        event.setDropItems(false);
        event.setExpToDrop(0);

        plugin.getServer().getScheduler().runTask(plugin,
                () -> block.setBlockData(data, false));
    }

    private boolean isWool(Material m) {
        return m != null && m.name().endsWith("_WOOL");
    }
}
