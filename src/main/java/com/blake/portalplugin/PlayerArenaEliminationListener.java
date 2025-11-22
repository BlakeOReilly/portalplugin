package com.blake.portalplugin.listeners;

import com.blake.portalplugin.ArenaEliminationHandler;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerArenaEliminationListener implements Listener {

    private final ArenaManager arenaManager;
    private final ArenaEliminationHandler eliminationHandler;

    public PlayerArenaEliminationListener(ArenaManager arenaManager,
                                          ArenaEliminationHandler eliminationHandler) {
        this.arenaManager = arenaManager;
        this.eliminationHandler = eliminationHandler;
    }

    /**
     * Once an arena has started, if a player in that arena falls below Y = -65,
     * that player is eliminated.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        if (event.getTo().getY() >= -65.0) return;

        var player = event.getPlayer();
        Arena arena = arenaManager.getArenaPlayerIsIn(player);
        if (arena == null) return;
        if (!arena.hasStarted()) return;

        eliminationHandler.eliminatePlayer(player, arena, "fell out of the arena", true);
    }
}
