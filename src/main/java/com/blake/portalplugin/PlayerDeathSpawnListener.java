// src/main/java/com/blake/portalplugin/listeners/PlayerDeathSpawnListener.java
package com.blake.portalplugin.listeners;

import com.blake.portalplugin.ArenaEliminationHandler;
import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.HubSpawnManager;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public class PlayerDeathSpawnListener implements Listener {

    private final Plugin plugin;
    private final ArenaManager arenaManager;
    private final ArenaEliminationHandler eliminationHandler;
    private final GameStateManager gameStateManager;
    private final HubSpawnManager hubSpawnManager;

    public PlayerDeathSpawnListener(ArenaManager arenaManager,
                                   ArenaEliminationHandler eliminationHandler,
                                   GameStateManager gameStateManager,
                                   HubSpawnManager hubSpawnManager,
                                   Plugin plugin) {
        this.arenaManager = arenaManager;
        this.eliminationHandler = eliminationHandler;
        this.gameStateManager = gameStateManager;
        this.hubSpawnManager = hubSpawnManager;
        this.plugin = plugin;
    }

    /**
     * STEP 1: Handle what happens when the player dies.
     *
     * - If in an arena that has started → elimination (winner logic handled by ArenaEliminationHandler)
     * - If in an arena that has NOT started yet → treat like leaving during countdown:
     *     - remove from arena
     *     - abort countdown if < 2 players remain
     * - In all cases → set gamestate to HUB
     *
     * NEW:
     * - If death happened in an arena → force item drops and instant respawn (no respawn screen)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();
        Arena arena = arenaManager.getArenaPlayerIsIn(player);

        boolean diedInArena = (arena != null);

        // If they died in an arena, ensure their items drop (even if something set keepInventory elsewhere).
        if (diedInArena) {
            // Ensure vanilla-style drops happen
            event.setKeepInventory(false);
            event.setKeepLevel(false);

            // Optional: if you want no EXP drops in arenas, uncomment:
            // event.setDroppedExp(0);
        }

        if (arena != null) {
            if (arena.hasStarted()) {
                // In-game death → elimination
                eliminationHandler.eliminatePlayer(player, arena, "died", false);
            } else {
                // Pre-game death during countdown:
                arena.removePlayer(player);
                arena.abortCountdownIfNotEnoughPlayers();
            }
        }

        // After any death, place them logically back into HUB state
        gameStateManager.setGameState(player, GameState.HUB);

        // NEW: instantly respawn arena deaths to avoid respawn screen.
        if (diedInArena) {
            // Run next tick so death is fully processed and respawn event fires cleanly.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                // If they are still dead, force respawn.
                // (Player#spigot().respawn() exists on Spigot/Paper)
                if (player.isDead()) {
                    try {
                        player.spigot().respawn();
                        plugin.getLogger().info("[PortalPlugin] Forced instant respawn for " + player.getName()
                                + " (arena='" + arena.getName() + "')");
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[PortalPlugin] Failed to force respawn via player.spigot().respawn(): " + t.getMessage());
                    }
                }
            });
        }
    }

    /**
     * STEP 2: FORCE respawn location to our hub spawn for all players.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {

        Location hub = hubSpawnManager.getHubSpawn();
        if (hub != null) {
            event.setRespawnLocation(hub);
        }

        // Ensure that after respawn, they are in HUB state
        gameStateManager.setGameState(event.getPlayer(), GameState.HUB);
    }
}