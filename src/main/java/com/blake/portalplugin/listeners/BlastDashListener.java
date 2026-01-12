package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;


public class BlastDashListener implements Listener {

    private static final long DASH_COOLDOWN_MS = 5000L;

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final BlastCooldownTracker cooldownTracker;

    public BlastDashListener(PortalPlugin plugin, GameStateManager gameStateManager, BlastCooldownTracker cooldownTracker) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
        this.cooldownTracker = cooldownTracker;
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
        if (cooldownTracker != null
                && !cooldownTracker.isReady(player.getUniqueId(), BlastCooldownTracker.CooldownType.DASH, now)) {
            return;
        }

        if (cooldownTracker != null) {
            cooldownTracker.startCooldown(player.getUniqueId(), BlastCooldownTracker.CooldownType.DASH, DASH_COOLDOWN_MS, now);
        }

        Vector dir = player.getLocation().getDirection().normalize();
        double speed = 0.7 + (Math.max(0, distance - 3) * 0.1);
        Vector vel = dir.multiply(speed);
        vel.setY(Math.max(0.1, vel.getY()));
        player.setVelocity(vel);
    }
}
