package com.blake.portalplugin;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;

public class SignListener implements Listener {
    private final GameStateManager manager;

    public SignListener(GameStateManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (manager.getGameState(p) != GameState.SPLEEF) return;
        // Additional logic for interacting with the sign
    }
}
