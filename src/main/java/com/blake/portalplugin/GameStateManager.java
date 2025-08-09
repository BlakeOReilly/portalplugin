package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameStateManager {
    private final HubStatsPlugin plugin;
    private final CustomScoreboardManager scoreboard;
    private GameQueueManager queueManager;

    private final Map<UUID, GameState> states = new HashMap<>();

    /** Which arena a player is assigned to ("red" or "blue") */
    private final Map<UUID, String> playerArena = new HashMap<>();

    /** Active match players per arena (exactly two UUIDs when a match is running) */
    private final Map<String, LinkedHashSet<UUID>> arenaPlayers = new HashMap<>();

    public GameStateManager(HubStatsPlugin plugin, CustomScoreboardManager scoreboard) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
        arenaPlayers.put("red", new LinkedHashSet<>());
        arenaPlayers.put("blue", new LinkedHashSet<>());
    }

    /** Called by GameQueueManager after construction */
    void setQueueManager(GameQueueManager q) { this.queueManager = q; }

    public GameState getGameState(Player p) {
        return states.getOrDefault(p.getUniqueId(), GameState.HUB);
    }

    public void clearPlayer(Player p) {
        states.remove(p.getUniqueId());
        playerArena.remove(p.getUniqueId());
        scoreboard.clear(p);
    }

    public void setGameState(Player p, GameState state, String arena) {
        states.put(p.getUniqueId(), state);
        switch (state) {
            case HUB -> {
                // Remove from any arena tracking
                String a = playerArena.remove(p.getUniqueId());
                if (a != null) {
                    LinkedHashSet<UUID> set = arenaPlayers.get(a);
                    if (set != null) set.remove(p.getUniqueId());
                }
                p.setGameMode(GameMode.SURVIVAL);
                p.setAllowFlight(false);
                p.getInventory().clear();
                plugin.giveHubCompass(p);
                Location hub = plugin.getHubLocation();
                if (hub != null) p.teleport(hub);
                scoreboard.applyHubBoard(p);
                p.sendMessage("§aYou are now in the HUB.");
            }
            case QUEUING -> {
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                int pos = queueManager != null ? queueManager.getQueuePosition(p) : -1;
                scoreboard.applyQueueBoard(p, pos);
                p.sendMessage("§eQueued for Spleef. Position: §f" + pos);
            }
            case GAMEPREP -> {
                if (arena == null) arena = "red";
                playerArena.put(p.getUniqueId(), arena);
                arenaPlayers.computeIfAbsent(arena, k -> new LinkedHashSet<>()).add(p.getUniqueId());
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                if (arena.equals("red")) p.teleport(plugin.getArenaManager().getRedSpawn());
                else p.teleport(plugin.getArenaManager().getBlueSpawn());
                scoreboard.applyGamePrepBoard(p, arena);
                p.sendMessage("§bGame starting soon in the §f" + arena + " §barena...");
            }
            case SPLEEF -> {
                // Keep arena assignment; just enable survival
                p.setGameMode(GameMode.SURVIVAL);
                p.sendMessage("§6Fight!");
            }
            case ADMIN -> {
                p.setGameMode(GameMode.CREATIVE);
                p.setAllowFlight(true);
                p.sendMessage("§cADMIN mode enabled.");
            }
            case MODESPLEEF -> {
                p.setGameMode(GameMode.SURVIVAL);
                p.sendMessage("§dSpleef mode.");
            }
        }
    }

    /** Called when two players have been matched */
    public void startGamePrepFor(String arena, Player p1, Player p2) {
        // Mark arena busy
        plugin.getArenaManager().setRedActive(arena.equals("red"));
        plugin.getArenaManager().setBlueActive(arena.equals("blue"));

        // Reset tracking for this arena, add both players
        LinkedHashSet<UUID> set = new LinkedHashSet<>();
        set.add(p1.getUniqueId());
        set.add(p2.getUniqueId());
        arenaPlayers.put(arena, set);

        setGameState(p1, GameState.GAMEPREP, arena);
        setGameState(p2, GameState.GAMEPREP, arena);

        // simple countdown then start
        new BukkitRunnable() {
            int seconds = 5;
            @Override public void run() {
                if (!p1.isOnline() || !p2.isOnline()) {
                    // Someone left; end pre-match
                    endMatch(arena, p1.isOnline() ? p1 : null, p2.isOnline() ? p2 : null);
                    cancel();
                    return;
                }
                if (seconds <= 0) {
                    setGameState(p1, GameState.SPLEEF, arena);
                    setGameState(p2, GameState.SPLEEF, arena);
                    cancel();
                    return;
                }
                p1.sendTitle("§bStarting in", "§f" + seconds, 0, 20, 0);
                p2.sendTitle("§bStarting in", "§f" + seconds, 0, 20, 0);
                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Eliminate a player from their current match.
     * Resolves opponent via arena tracking and ends the match.
     */
    public void eliminate(Player eliminated) {
        UUID loserId = eliminated.getUniqueId();
        String arena = playerArena.get(loserId);
        if (arena == null) {
            // Not in a tracked arena; just send to hub to be safe
            setGameState(eliminated, GameState.HUB, null);
            return;
        }

        LinkedHashSet<UUID> set = arenaPlayers.get(arena);
        Player winner = null;

        if (set != null && set.size() >= 1) {
            for (UUID id : set) {
                if (!id.equals(loserId)) {
                    Player candidate = Bukkit.getPlayer(id);
                    if (candidate != null && candidate.isOnline()) {
                        winner = candidate;
                        break;
                    }
                }
            }
        }

        endMatch(arena, winner, eliminated);
    }

    /** Called when a match ends; return both players to hub and free arena */
    public void endMatch(String arena, Player winner, Player loser) {
        if (winner != null) {
            winner.sendMessage("§aYou won!");
            setGameState(winner, GameState.HUB, null);
        }
        if (loser != null) {
            loser.sendMessage("§cYou were eliminated.");
            setGameState(loser, GameState.HUB, null);
        }

        // Clear arena tracking
        LinkedHashSet<UUID> set = arenaPlayers.get(arena);
        if (set != null) set.clear();

        // Reset/restore arena
        if (arena.equals("red")) {
            plugin.getArenaManager().restoreRedArena();
            plugin.getArenaManager().setRedActive(false);
        } else {
            plugin.getArenaManager().restoreBlueArena();
            plugin.getArenaManager().setBlueActive(false);
        }
    }
}
