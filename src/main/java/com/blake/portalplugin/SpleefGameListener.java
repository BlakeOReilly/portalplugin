package com.blake.portalplugin;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

public class SpleefGameListener implements Listener {
    private final GameStateManager manager;

    public SpleefGameListener(GameStateManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerFall(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (manager.getGameState(p) != GameState.SPLEEF) return;

        Location to = event.getTo();
        if (to != null && to.getY() < -62) {
            manager.eliminate(p);
        }
    }
}
