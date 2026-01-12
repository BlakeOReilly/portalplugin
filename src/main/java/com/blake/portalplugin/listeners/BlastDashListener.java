package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlastDashListener implements Listener {

    private static final long DASH_COOLDOWN_MS = 5000L;

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final Map<UUID, Long> lastDashMs = new HashMap<>();

    public BlastDashListener(PortalPlugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        var player = e.getPlayer();
        if (player == null) return;
        if (gameStateManager.getGameState(player) != GameState.BLAST) return;

        ItemStack item = e.getItem();
        if (item == null) return;
        if (!BlastItems.isBasicBlaster(plugin, item) && !BlastItems.isBigBlaster(plugin, item)) return;

        BlastPowerupManager pm = plugin.getBlastPowerupManager();
        if (pm == null) return;

        int distance = pm.getDashDistanceBlocks(player);
        if (distance <= 0) return;

        long now = System.currentTimeMillis();
        long last = lastDashMs.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < DASH_COOLDOWN_MS) return;

        lastDashMs.put(player.getUniqueId(), now);

        Vector dir = player.getLocation().getDirection().normalize();
        double speed = 0.7 + (Math.max(0, distance - 3) * 0.1);
        Vector vel = dir.multiply(speed);
        vel.setY(Math.max(0.1, vel.getY()));
        player.setVelocity(vel);
    }
}
