package com.blake.portalplugin.listeners;

import com.blake.portalplugin.ArenaEliminationHandler;
import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SumoVoidEliminationListener implements Listener {

    private final ArenaManager arenaManager;
    private final ArenaEliminationHandler eliminationHandler;
    private final GameStateManager gameStateManager;

    // Prevents repeated eliminate calls while the player is still falling/moving
    private final Set<UUID> recentlyEliminated = ConcurrentHashMap.newKeySet();

    public SumoVoidEliminationListener(ArenaManager arenaManager,
                                      ArenaEliminationHandler eliminationHandler,
                                      GameStateManager gameStateManager) {
        this.arenaManager = arenaManager;
        this.eliminationHandler = eliminationHandler;
        this.gameStateManager = gameStateManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        if (gameStateManager.getGameState(player) != GameState.SUMO) {
            recentlyEliminated.remove(player.getUniqueId());
            return;
        }

        // Only care when their Y is at/below the threshold
        if (player.getLocation().getY() > -61.0) return;

        UUID uuid = player.getUniqueId();
        if (!recentlyEliminated.add(uuid)) return;

        Arena arena = arenaManager.getArenaPlayerIsIn(player);
        if (arena == null || !arena.hasStarted()) return;

        eliminationHandler.eliminatePlayer(player, arena, "Fell", true);
    }
}