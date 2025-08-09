package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class ArenaManager {
    private final HubStatsPlugin plugin;

    private boolean redActive = false;
    private boolean blueActive = false;

    public ArenaManager(HubStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRedActive() { return redActive; }
    public void setRedActive(boolean active) { this.redActive = active; }

    public boolean isBlueActive() { return blueActive; }
    public void setBlueActive(boolean active) { this.blueActive = active; }

    public Location getRedSpawn() {
        World w = Bukkit.getWorlds().get(0);
        return new Location(w, 20.5, w.getSpawnLocation().getY(), 0.5, 0, 0);
        // Replace with your actual arena coords
    }

    public Location getBlueSpawn() {
        World w = Bukkit.getWorlds().get(0);
        return new Location(w, -20.5, w.getSpawnLocation().getY(), 0.5, 180, 0);
    }

    public void restoreRedArena() {
        // TODO: reset red arena blocks if needed
        plugin.getLogger().info("Restored red arena.");
    }

    public void restoreBlueArena() {
        // TODO: reset blue arena blocks if needed
        plugin.getLogger().info("Restored blue arena.");
    }
}
