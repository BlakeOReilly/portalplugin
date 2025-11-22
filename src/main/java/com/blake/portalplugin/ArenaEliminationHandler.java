package com.blake.portalplugin;

import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import com.blake.portalplugin.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class ArenaEliminationHandler {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final HubSpawnManager hubSpawnManager;
    private final ArenaManager arenaManager;
    private final StatsManager statsManager;

    public ArenaEliminationHandler(PortalPlugin plugin,
                                   GameStateManager gameStateManager,
                                   HubSpawnManager hubSpawnManager,
                                   ArenaManager arenaManager,
                                   StatsManager statsManager) {

        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
        this.hubSpawnManager = hubSpawnManager;
        this.arenaManager = arenaManager;
        this.statsManager = statsManager;
    }

    public void eliminatePlayer(Player player, Arena arena, String reason, boolean teleportToHub) {
        if (player == null || arena == null) return;
        if (!arena.isPlayerInArena(player)) return;

        arena.removePlayer(player);

        String msg = "&c" + player.getName() + " has been eliminated!";
        if (reason != null && !reason.isBlank()) {
            msg += " &7(" + reason + ")";
        }
        arena.broadcast(msg);

        if (teleportToHub && player.isOnline()) {
            Location hubSpawn = hubSpawnManager.getHubSpawn();
            if (hubSpawn != null) {
                player.teleport(hubSpawn);
            }
        }

        gameStateManager.setGameState(player, GameState.HUB);

        int remaining = arena.getPlayers().size();

        if (!arena.hasStarted()) {
            arena.abortCountdownIfNotEnoughPlayers();
            return;
        }

        // WIN CONDITION
        if (remaining == 1) {
            UUID winnerId = arena.getPlayers().get(0);
            Player winner = Bukkit.getPlayer(winnerId);

            if (winner != null && winner.isOnline()) {

                arena.broadcast("&a" + winner.getName() + " has WON the game!");

                // NEW: teleport winner to global win location if set
                Location winLoc = plugin.getWinLocationManager().getWinLocation();

                if (winLoc != null) {
                    winner.teleport(winLoc);
                } else {
                    // fallback
                    Location hub = hubSpawnManager.getHubSpawn();
                    if (hub != null) winner.teleport(hub);
                }

                gameStateManager.setGameState(winner, GameState.HUB);
            }

            Bukkit.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                    + "' winner: " + (winnerId != null ? winnerId : "null"));

            Set<UUID> participants = arena.getAllPlayersEverJoined();
            Bukkit.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                    + "' participants for stats: " + participants.size());

            if (statsManager != null && winnerId != null && !participants.isEmpty()) {
                statsManager.recordGameResult("spleef", winnerId, participants);
            } else {
                Bukkit.getLogger().warning("[PortalPlugin] Stats not recorded: "
                        + "statsManager=" + (statsManager != null)
                        + ", winnerId=" + (winnerId != null)
                        + ", participantsEmpty=" + participants.isEmpty());
            }

            arena.resetArenaAfterWin();
            arena.clearParticipantsAfterStats();
            arenaManager.resetArenaBlocks(arena);
            return;
        }

        if (remaining == 0) {
            arena.resetArenaAfterWin();
            arena.clearParticipantsAfterStats();
            arenaManager.resetArenaBlocks(arena);
        }
    }
}
