package com.blake.portalplugin;

import org.bukkit.Location;

import java.util.*;

public class BlastMapData {

    private final String name;
    private final String worldName;
    private final EnumMap<BlastTeam, List<Location>> spawnsByTeam = new EnumMap<>(BlastTeam.class);

    public BlastMapData(String name, String worldName) {
        this.name = (name == null ? "unknown" : name.trim().toLowerCase());
        this.worldName = (worldName == null ? null : worldName.trim());
        for (BlastTeam t : BlastTeam.values()) {
            spawnsByTeam.put(t, new ArrayList<>());
        }
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public List<Location> getSpawns(BlastTeam team) {
        if (team == null) return Collections.emptyList();
        return spawnsByTeam.getOrDefault(team, Collections.emptyList());
    }

    public void setSpawns(BlastTeam team, List<Location> spawns) {
        if (team == null) return;
        spawnsByTeam.put(team, spawns == null ? new ArrayList<>() : new ArrayList<>(spawns));
    }

    public boolean hasRequiredSpawnsPerTeam(int requiredPerTeam) {
        int req = Math.max(1, requiredPerTeam);
        for (BlastTeam t : BlastTeam.values()) {
            if (getSpawns(t).size() < req) return false;
        }
        return true;
    }

    public Location getSpawn(BlastTeam team, int index) {
        List<Location> list = getSpawns(team);
        if (list.isEmpty()) return null;
        if (index < 0) index = 0;
        if (index >= list.size()) return null;
        return list.get(index);
    }
}
