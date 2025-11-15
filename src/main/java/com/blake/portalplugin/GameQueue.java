package com.blake.portalplugin.queues;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameQueue {

    private final String name;
    private final List<UUID> queuedPlayers = new ArrayList<>();

    public GameQueue(String name) {
        this.name = name.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public void addPlayer(Player p) {
        if (!queuedPlayers.contains(p.getUniqueId())) {
            queuedPlayers.add(p.getUniqueId());
        }
    }

    public void removePlayer(Player p) {
        queuedPlayers.remove(p.getUniqueId());
    }

    public boolean isPlayerQueued(Player p) {
        return queuedPlayers.contains(p.getUniqueId());
    }

    public List<UUID> getQueuedPlayers() {
        return queuedPlayers;
    }

    public int size() {
        return queuedPlayers.size();
    }
}
