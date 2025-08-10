package com.blake.portalplugin;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class HubStatsPlugin extends JavaPlugin {
    private ArenaManager arenaManager;
    private Location hubLocation;

    @Override
    public void onEnable() {
        // Initialize managers and state
        // ArenaManager requires this plugin instance in its constructor
        arenaManager = new ArenaManager(this);

        // Other initialization can go here (listeners, commands, etc.)
    }

    @Override
    public void onDisable() {
        // Cleanup if necessary
    }

    // Accessor for other classes
    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    // Minimal placeholder implementations so other classes compiling against
    // these methods will succeed. Real implementations should replace these.
    public String getPlayerRank(Player player) {
        // TODO: integrate with real rank system
        return "Member";
    }

    public int getPlayerCoins(Player player) {
        // TODO: integrate with economy/storage
        return 0;
    }

    public void giveHubCompass(Player player) {
        // TODO: give the player a compass item pointing to hub
    }

    public Location getHubLocation() {
        return hubLocation;
    }

    public void setHubLocation(Location loc) {
        this.hubLocation = loc;
    }
}