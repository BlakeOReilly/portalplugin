package com.blake.portalplugin.listeners;

import com.blake.portalplugin.BlastMap;
import com.blake.portalplugin.BlastMinigameManager;
import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.PortalPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class BlastCeilingListener implements Listener {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;

    public BlastCeilingListener(PortalPlugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Location to = event.getTo();
        if (to == null) return;

        Integer ceilingY = resolveCeilingY(player, to);
        if (ceilingY == null) return;

        if (to.getY() > ceilingY) {
            Location capped = to.clone();
            capped.setY(ceilingY);
            event.setTo(capped);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Location to = event.getTo();
        if (to == null) return;

        Integer ceilingY = resolveCeilingY(player, to);
        if (ceilingY == null) return;

        if (to.getY() > ceilingY) {
            Location capped = to.clone();
            capped.setY(ceilingY);
            event.setTo(capped);
        }
    }

    private Integer resolveCeilingY(Player player, Location destination) {
        if (player == null || destination == null) return null;
        if (gameStateManager.getGameState(player) != GameState.BLAST) return null;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress() || !bm.isParticipant(player)) return null;

        BlastMap map = bm.getActiveMap();
        if (map == null) return null;

        String worldName = map.getWorldName();
        if (worldName != null && destination.getWorld() != null
                && !worldName.equalsIgnoreCase(destination.getWorld().getName())) {
            return null;
        }

        return map.getCeilingY();
    }
}
