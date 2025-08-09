package com.blake.portalplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

public class GameStateListener implements Listener {

    private final GameStateManager manager;

    public GameStateListener(GameStateManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        GameState state = manager.getGameState(p);
        if (state == GameState.HUB || state == GameState.QUEUING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            GameState state = manager.getGameState(p);
            if (state == GameState.HUB || state == GameState.QUEUING) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        GameState state = manager.getGameState(p);
        if (state == GameState.HUB || state == GameState.QUEUING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            GameState state = manager.getGameState(player);
            if (state == GameState.HUB || state == GameState.QUEUING) {
                event.setCancelled(true);
                event.setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        GameState state = manager.getGameState(p);
        if ((state == GameState.HUB || state == GameState.QUEUING)
                && (event.getAction() == Action.PHYSICAL)) {
            event.setCancelled(true);
        }
    }
}
