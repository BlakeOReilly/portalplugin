package com.blake.portalplugin.listeners;

import com.blake.portalplugin.ArenaEliminationHandler;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.HubSpawnManager;
import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.BlastMap;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import com.blake.portalplugin.queues.GameQueueManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final PortalPlugin plugin;
    private final GameStateManager manager;
    private final ArenaManager arenaManager;
    private final GameQueueManager queueManager;
    private final ArenaEliminationHandler eliminationHandler;
    private final HubSpawnManager hubSpawnManager;

    public PlayerJoinQuitListener(PortalPlugin plugin,
                                  GameStateManager manager,
                                  ArenaManager arenaManager,
                                  GameQueueManager queueManager,
                                  ArenaEliminationHandler eliminationHandler,
                                  HubSpawnManager hubSpawnManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.arenaManager = arenaManager;
        this.queueManager = queueManager;
        this.eliminationHandler = eliminationHandler;
        this.hubSpawnManager = hubSpawnManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.ensureDefault(e.getPlayer());

        Location joinSpawn = null;
        if (plugin.getBlastMinigameManager() != null) {
            String activeMap = plugin.getConfig().getString("blast.active-map", "");
            if (activeMap != null && !activeMap.isBlank()) {
                BlastMap map = plugin.getBlastMinigameManager().getMapStore().getMap(activeMap.trim().toLowerCase());
                if (map != null) {
                    joinSpawn = map.getStartSpawn();
                }
            }
        }

        if (joinSpawn == null) {
            joinSpawn = hubSpawnManager.getHubSpawn();
        }

        if (joinSpawn != null) {
            e.getPlayer().teleport(joinSpawn);
        }

        // If this server is configured as a "minigame hub", auto-queue on join
        if (plugin.getMinigameQueueManager() != null && plugin.isMinigameHub()) {
            plugin.getMinigameQueueManager().handleJoin(e.getPlayer());
        } else {
            e.getPlayer().sendMessage("§7[GameState] Defaulted to HUB.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {

        // Always remove from sign-based queues so players can't "stick" if they DC
        try {
            if (queueManager != null) queueManager.removePlayerFromAllQueues(e.getPlayer());
        } catch (Exception ignored) {}

        // Remove from minigame hub queue (and stop countdown if needed)
        try {
            if (plugin.getMinigameQueueManager() != null) {
                plugin.getMinigameQueueManager().handleQuit(e.getPlayer());
            }
        } catch (Exception ignored) {}

        Arena arena = arenaManager.getArenaPlayerIsIn(e.getPlayer());

        if (arena != null && arena.hasStarted()) {
            eliminationHandler.eliminatePlayer(e.getPlayer(), arena, "disconnected", false);
        } else if (arena != null && !arena.hasStarted()) {

            arena.getPlayers().remove(e.getPlayer().getUniqueId());

            if (arena.getPlayers().size() < 2) {
                arena.broadcast("&cPlayer left – countdown stopped.");
                arena.clearCountdown();
            }

            if (arena.getPlayers().isEmpty()) {
                arena.setInUse(false);
                arena.setStarted(false);
                arena.clearCountdown();
            }
        }

        manager.clear(e.getPlayer());
    }
}
