package com.blake.portalplugin.queues;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class GameQueueManager {

    private final Map<String, GameQueue> queues = new HashMap<>();
    private final List<String> signEntries = new ArrayList<>();

    private final ArenaManager arenaManager;
    private final GameStateManager gameStateManager;

    private final Plugin plugin;

    public GameQueueManager(ArenaManager arenaManager, GameStateManager gameStateManager, Plugin plugin) {
        this.arenaManager = arenaManager;
        this.gameStateManager = gameStateManager;
        this.plugin = plugin;
    }

    // ---------------------------------------------------------
    // Queue management
    // ---------------------------------------------------------
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

    public List<String> getQueueNames() {
        return new ArrayList<>(queues.keySet());
    }

    public void loadQueuesFromConfig(List<String> names) {
        if (names == null) return;
        for (String q : names) {
            if (q == null || q.isBlank()) continue;
            String key = q.toLowerCase();
            if (!queues.containsKey(key)) {
                createQueue(key);
            }
        }
    }

    // ---------------------------------------------------------
    // Sign Persistence
    // ---------------------------------------------------------

    public void loadSignsFromConfig(List<String> entries) {
        signEntries.clear();
        if (entries != null) {
            for (String s : entries) {
                if (s != null && !s.isBlank()) {
                    signEntries.add(s);
                }
            }
        }
    }

    public List<String> getSignEntries() {
        return new ArrayList<>(signEntries);
    }

    /**
     * Register sign & immediately write to config.yml
     */
    public void registerSign(Block block, String game) {
        if (block == null || game == null) return;

        String entry = block.getWorld().getName() + "," +
                block.getX() + "," +
                block.getY() + "," +
                block.getZ() + "," +
                game.toLowerCase();

        if (!signEntries.contains(entry)) {
            signEntries.add(entry);
        }

        // Immediately persist the list of sign entries
        plugin.getConfig().set("queue-signs", new ArrayList<>(signEntries));
        plugin.saveConfig();
    }

    /**
     * Restore metadata for all saved queue signs after startup.
     */
    public void restoreSignMetadata(Plugin plugin) {
        for (String entry : signEntries) {

            String[] parts = entry.split(",");
            if (parts.length != 5) continue;

            String worldName = parts[0];
            int x, y, z;

            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (Exception e) {
                continue;
            }

            String game = parts[4];
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Block block = world.getBlockAt(x, y, z);

            // Re-attach metadata so clicks are recognized
            block.setMetadata("queue_sign", new FixedMetadataValue(plugin, game));
        }
    }

    /**
     * Destroy all registered queue signs in the world, clear metadata,
     * and reset the signEntries list + config storage.
     *
     * @return number of sign blocks we attempted to clear
     */
    public int clearAllSigns() {
        int cleared = 0;

        for (String entry : new ArrayList<>(signEntries)) {
            String[] parts = entry.split(",");
            if (parts.length != 5) continue;

            String worldName = parts[0];
            int x, y, z;

            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (Exception e) {
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Block block = world.getBlockAt(x, y, z);

            // Remove metadata if present
            if (block.hasMetadata("queue_sign")) {
                block.removeMetadata("queue_sign", plugin);
            }

            // If it is still a sign block, break it
            Material type = block.getType();
            if (type.name().endsWith("_SIGN")) {
                block.setType(Material.AIR, false);
                cleared++;
            }
        }

        // Clear in-memory list
        signEntries.clear();

        // Reset config storage for queue-signs
        plugin.getConfig().set("queue-signs", new ArrayList<String>());
        plugin.saveConfig();

        return cleared;
    }

    // ---------------------------------------------------------
    // Queue logic for arenas
    // ---------------------------------------------------------
    public void handlePlayerQueued(String gameName) {
        if (gameName == null) return;

        String key = gameName.toLowerCase();
        GameQueue queue = queues.get(key);
        if (queue == null) return;

        Collection<Arena> allArenas = arenaManager.getAllArenas();
        Arena chosenArena = null;

        // Try to reuse an active arena that is in use, not started, and has space.
        for (Arena arena : allArenas) {
            if (arena.isInUse()
                    && !arena.hasStarted()
                    && arena.getPlayers().size() < arena.getMaxPlayers()) {
                chosenArena = arena;
                break;
            }
        }

        // No active waiting arena found: require at least 2 queued players, then allocate a fresh arena.
        if (chosenArena == null) {
            if (queue.size() <= 1) {
                // Not enough players to justify starting a new arena
                return;
            }

            for (Arena arena : allArenas) {
                if (!arena.isInUse()
                        && arena.getPlayers().size() < arena.getMaxPlayers()) {
                    chosenArena = arena;
                    arena.setInUse(true);
                    break;
                }
            }
        }

        if (chosenArena == null) return;
        if (chosenArena.getSpawnPoints().isEmpty()) return;

        List<UUID> snapshot = new ArrayList<>(queue.getQueuedPlayers());
        List<UUID> remove = new ArrayList<>();

        int available = chosenArena.getMaxPlayers() - chosenArena.getPlayers().size();
        if (available <= 0) return;

        int spawnCount = chosenArena.getSpawnPoints().size();
        int assigned = 0;

        for (UUID uuid : snapshot) {

            if (assigned >= available) break;

            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                remove.add(uuid);
                continue;
            }

            Location spawn = chosenArena.getSpawnPoints().get(assigned % spawnCount);
            chosenArena.addPlayer(p);

            p.teleport(spawn);
            gameStateManager.setGameState(p, GameState.ARENA);
            p.sendMessage("You have been moved to arena '" + chosenArena.getName() + "'.");

            remove.add(uuid);
            assigned++;
        }

        queue.getQueuedPlayers().removeAll(remove);

        if (!chosenArena.hasCountdown()
                && !chosenArena.hasStarted()
                && chosenArena.getPlayers().size() >= 2) {

            chosenArena.broadcast("&aEnough players joined! Countdown started.");
            chosenArena.startCountdown();
        }
    }
}
