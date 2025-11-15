package com.blake.portalplugin.queues;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GameQueueManager {

    private final Map<String, GameQueue> queues = new HashMap<>();
    private final ArenaManager arenaManager;
    private final GameStateManager gameStateManager;

    public GameQueueManager(ArenaManager arenaManager, GameStateManager gameStateManager) {
        this.arenaManager = arenaManager;
        this.gameStateManager = gameStateManager;
    }

    // -----------------------------
    // Basic queue management
    // -----------------------------

    public boolean queueExists(String name) {
        return queues.containsKey(name.toLowerCase());
    }

    public GameQueue createQueue(String name) {
        GameQueue queue = new GameQueue(name);
        queues.put(name.toLowerCase(), queue);
        return queue;
    }

    public GameQueue getQueue(String name) {
        return queues.get(name.toLowerCase());
    }

    public Map<String, GameQueue> getAllQueues() {
        return queues;
    }

    // -----------------------------
    // New logic: handle queued player
    // -----------------------------

    public void handlePlayerQueued(String gameName, Player player) {
        String key = gameName.toLowerCase();
        GameQueue queue = queues.get(key);
        if (queue == null) {
            return;
        }

        // Only run when there is more than one player in the queue
        if (queue.size() <= 1) {
            return;
        }

        // 1) Look for an arena that is in use and not started, and not full
        Arena chosenArena = null;

        Collection<Arena> allArenas = arenaManager.getAllArenas();
        for (Arena arena : allArenas) {
            if (arena.isInUse() && !arena.hasStarted()) {
                if (arena.getPlayers().size() < arena.getMaxPlayers()) {
                    chosenArena = arena;
                    break;
                }
            }
        }

        // 2) If none, look for an arena that is not in use and not full
        if (chosenArena == null) {
            for (Arena arena : allArenas) {
                if (!arena.isInUse()) {
                    if (arena.getPlayers().size() < arena.getMaxPlayers()) {
                        chosenArena = arena;
                        arena.setInUse(true);
                        break;
                    }
                }
            }
        }

        if (chosenArena == null) {
            player.sendMessage("No available arenas for this game right now.");
            return;
        }

        // Get a spawn point for this arena
        if (chosenArena.getSpawnPoints().isEmpty()) {
            player.sendMessage("Arena '" + chosenArena.getName() + "' has no spawn points set.");
            return;
        }

        Location spawn = chosenArena.getSpawnPoints().get(0); // first spawn for now

        // Add player to arena list
        chosenArena.addPlayer(player);

        // Teleport player and set gamestate to ARENA
        player.teleport(spawn);
        gameStateManager.setGameState(player, GameState.ARENA);
        player.sendMessage("You have been moved to arena '" + chosenArena.getName() + "'.");
    }
}
