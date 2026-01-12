// src/main/java/com/blake/portalplugin/listeners/BlastGeneratorListener.java
package com.blake.portalplugin.listeners;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemDespawnEvent;

public class BlastGeneratorListener implements Listener {

    private final GameStateManager gameStateManager;

    public BlastGeneratorListener(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        Block placed = e.getBlockPlaced();
        if (placed == null) return;

        // Disallow placing on the 1st or 2nd block above any generator block:
        // - REDSTONE_BLOCK (team generators)
        // - DIAMOND_BLOCK (middle generator)
        Block below1 = placed.getRelative(0, -1, 0);
        Block below2 = placed.getRelative(0, -2, 0);

        Material b1 = below1.getType();
        Material b2 = below2.getType();

        boolean onGenerator =
                b1 == Material.REDSTONE_BLOCK || b2 == Material.REDSTONE_BLOCK
                        || b1 == Material.DIAMOND_BLOCK || b2 == Material.DIAMOND_BLOCK;

        if (onGenerator) {
            e.setCancelled(true);
            p.sendMessage("§c[BLAST] You cannot place blocks on generators.");
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent e) {
        if (e.getEntity() == null || e.getLocation() == null) return;

        // If the item is sitting on a generator block, do not despawn it.
        Block under = e.getLocation().getBlock().getRelative(0, -1, 0);
        Material m = under.getType();

        if (m == Material.REDSTONE_BLOCK || m == Material.DIAMOND_BLOCK) {
            e.setCancelled(true);
        }
    }
}
