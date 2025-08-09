package com.blake.portalplugin;

import org.bukkit.entity.Player;

import java.util.*;

public class GameQueueManager {
    private final HubStatsPlugin plugin;
    private final CustomScoreboardManager scoreboard;
    private final GameStateManager stateManager;

    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();

    public GameQueueManager(HubStatsPlugin plugin, CustomScoreboardManager scoreboard, GameStateManager stateManager) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
        this.stateManager = stateManager;
        // Link back so state manager can ask queue for position
        this.stateManager.setQueueManager(this);
    }

    public synchronized int addPlayerToQueue(Player p) {
        queue.add(p.getUniqueId());
        stateManager.setGameState(p, GameState.QUEUING, null);
        int pos = getQueuePosition(p);
        tryStartMatch();
        return pos;
    }

    public synchronized void removePlayer(Player p) {
        queue.remove(p.getUniqueId());
    }

    public synchronized int getQueuePosition(Player p) {
        int i = 1;
        for (UUID id : queue) {
            if (id.equals(p.getUniqueId())) return i;
            i++;
        }
        return -1;
    }

    public synchronized List<Player> getSnapshot() {
        List<Player> list = new ArrayList<>();
        for (UUID id : queue) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    /** Try to start a match if possible */
    private void tryStartMatch() {
        if (queue.size() < 2) return;

        String arena = chooseFreeArena();
        if (arena == null) {
            // no free arena yet
            return;
        }

        Iterator<UUID> it = queue.iterator();
        Player p1 = plugin.getServer().getPlayer(it.next()); it.remove();
        Player p2 = plugin.getServer().getPlayer(it.next()); it.remove();

        if (p1 == null || p2 == null) return;

        plugin.getArenaManager().setRedActive(arena.equals("red"));
        plugin.getArenaManager().setBlueActive(arena.equals("blue"));

        stateManager.startGamePrepFor(arena, p1, p2);
    }

    private String chooseFreeArena() {
        if (!plugin.getArenaManager().isRedActive()) return "red";
        if (!plugin.getArenaManager().isBlueActive()) return "blue";
        return null;
    }

    /** Clear all assignments for an arena (compat with older code) */
    public void clearArena(String arena) {
        // no-op in this simplified queue; matches free the arena in GameStateManager.endMatch
    }
}
