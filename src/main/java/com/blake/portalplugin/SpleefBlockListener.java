// src/main/java/com/blake/portalplugin/SpleefBlockListener.java
package com.blake.portalplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SpleefBlockListener implements Listener {
    private final GameStateManager manager;

    public SpleefBlockListener(GameStateManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (manager.getGameState(p) != GameState.SPLEEF) return;

        Block b = event.getClickedBlock();
        if (b == null) return;

        Material m = b.getType();
        if (m == Material.RED_WOOL || m == Material.BLUE_WOOL) {
            // break instantly without drop
            b.setType(Material.AIR);
            event.setCancelled(true);
        }
    }
}
