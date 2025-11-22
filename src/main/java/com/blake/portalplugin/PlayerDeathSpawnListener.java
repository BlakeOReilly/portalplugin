package com.blake.portalplugin.listeners;

import com.blake.portalplugin.ArenaEliminationHandler;
import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.HubSpawnManager;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerDeathSpawnListener implements Listener {

    private final ArenaManager arenaManager;
    private final ArenaEliminationHandler eliminationHandler;
    private final GameStateManager gameStateManager;
    private final HubSpawnManager hubSpawnManager;

    public PlayerDeathSpawnListener(ArenaManager arenaManager,
                                    ArenaEliminationHandler eliminationHandler,
                                    GameStateManager gameStateManager,
                                    HubSpawnManager hubSpawnManager) {
        this.arenaManager = arenaManager;
        this.eliminationHandler = eliminationHandler;
        this.gameStateManager = gameStateManager;
        this.hubSpawnManager = hubSpawnManager;
    }

    /**
     * STEP 1: Handle what happens when the player dies.
     *
     * - If in an arena that has started → elimination (winner logic handled by ArenaEliminationHandler)
     * - If in an arena that has NOT started yet → treat like leaving during countdown:
     *     - remove from arena
     *     - abort countdown if < 2 players remain
     * - In all cases → set gamestate to HUB
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();
        Arena arena = arenaManager.getArenaPlayerIsIn(player);

        if (arena != null) {
            if (arena.hasStarted()) {
                // In-game death → elimination
                eliminationHandler.eliminatePlayer(player, arena, "died", false);
            } else {
                // Pre-game death during countdown:
                // treat like a player leaving before the game starts.
                arena.removePlayer(player);
                arena.abortCountdownIfNotEnoughPlayers();
            }
        }

        // After any death, place them logically back into HUB state
        gameStateManager.setGameState(player, GameState.HUB);
    }

    /**
     * STEP 2: FORCE respawn location to our hub spawn for all players.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {

        Location hub = hubSpawnManager.getHubSpawn();
        if (hub != null) {
            event.setRespawnLocation(hub);
        }

        // Ensure that after respawn, they are in HUB state
        gameStateManager.setGameState(event.getPlayer(), GameState.HUB);
    }
}
