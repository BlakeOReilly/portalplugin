package com.blake.portalplugin.listeners;

import com.blake.portalplugin.ArenaEliminationHandler;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.HubSpawnManager;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final GameStateManager manager;
    private final ArenaManager arenaManager;
    private final ArenaEliminationHandler eliminationHandler;
    private final HubSpawnManager hubSpawnManager;

    public PlayerJoinQuitListener(GameStateManager manager,
                                  ArenaManager arenaManager,
                                  ArenaEliminationHandler eliminationHandler,
                                  HubSpawnManager hubSpawnManager) {
        this.manager = manager;
        this.arenaManager = arenaManager;
        this.eliminationHandler = eliminationHandler;
        this.hubSpawnManager = hubSpawnManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.ensureDefault(e.getPlayer());

        Location hubSpawn = hubSpawnManager.getHubSpawn();
        if (hubSpawn != null) {
            e.getPlayer().teleport(hubSpawn);
        }

        e.getPlayer().sendMessage(ChatColor.GRAY + "[GameState] Defaulted to HUB.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {

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
