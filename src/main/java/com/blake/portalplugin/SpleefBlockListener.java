package com.blake.portalplugin;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;

public class SpleefBlockListener implements Listener {
    private final GameStateManager manager;

    public SpleefBlockListener(GameStateManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = (Player) event.getPlayer();
        if (manager.getGameState(p) != GameState.SPLEEF) return;
        // Additional logic for block breaking in spleef
    }
}
