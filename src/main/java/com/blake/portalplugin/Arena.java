package com.blake.portalplugin.arenas;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Arena {

    private final String name;
    private boolean inUse;
    private boolean started;
    private int maxPlayers = 100;  // default value
    private final List<Location> spawnPoints = new ArrayList<>();
    private final List<UUID> playersInArena = new ArrayList<>();

    public Arena(String name) {
        this.name = name.toLowerCase();
        this.inUse = false;
        this.started = false;
    }

    public String getName() {
        return name;
    }

    // -------------------------
    // Arena State
    // -------------------------

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public boolean hasStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    // -------------------------
    // Spawn Points
    // -------------------------

    public void addSpawn(Location loc) {
        spawnPoints.add(loc);
    }

    public List<Location> getSpawnPoints() {
        return spawnPoints;
    }

    // -------------------------
    // Players
    // -------------------------

    public void addPlayer(Player player) {
        if (!playersInArena.contains(player.getUniqueId())) {
            playersInArena.add(player.getUniqueId());
        }
    }

    public void removePlayer(Player player) {
        playersInArena.remove(player.getUniqueId());
    }

    public boolean isPlayerInArena(Player player) {
        return playersInArena.contains(player.getUniqueId());
    }

    public List<UUID> getPlayers() {
        return playersInArena;
    }
}
