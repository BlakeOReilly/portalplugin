package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public class HubStatsPlugin extends JavaPlugin {
    private ArenaManager arenaManager;
    private Location hubLocation;

    @Override
    public void onEnable() {
        // Ensure config is present
        saveDefaultConfig();

        // Attempt to lazily initialize ArenaManager if needed elsewhere
        try {
            if (arenaManager == null) {
                try {
                    arenaManager = new ArenaManager(this);
                } catch (NoSuchMethodError | Exception e) {
                    try {
                        arenaManager = new ArenaManager();
                    } catch (NoSuchMethodError | Exception ignored) {
                        // If neither constructor exists, leave it null; callers should handle null at runtime.
                    }
                }
            }
        } catch (Throwable t) {
            // Defensive: do not fail plugin enable on unexpected errors here
            getLogger().warning("Failed to initialize ArenaManager during onEnable: " + t.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // nothing for now
    }

    // Getter for ArenaManager used throughout the codebase
    public ArenaManager getArenaManager() {
        if (arenaManager == null) {
            try {
                arenaManager = new ArenaManager(this);
            } catch (NoSuchMethodError | Exception e) {
                try {
                    arenaManager = new ArenaManager();
                } catch (NoSuchMethodError | Exception ignored) {
                    // leave null
                }
            }
        }
        return arenaManager;
    }

    // Minimal stub implementations for methods referenced by other classes to allow compilation.
    // Implement real logic as needed for your plugin.
    public String getPlayerRank(Player player) {
        if (player == null) return "Unknown";
        // Default placeholder rank
        return "Member";
    }

    public int getPlayerCoins(Player player) {
        // Default placeholder coins
        return 0;
    }

    public void giveHubCompass(Player player) {
        if (player == null) return;
        try {
            ItemStack compass = new ItemStack(Material.COMPASS, 1);
            ItemMeta meta = compass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("Hub Compass");
                compass.setItemMeta(meta);
            }
            player.getInventory().addItem(compass);
        } catch (Exception e) {
            // swallow to avoid breaking callers during compile-time testing
            getLogger().warning("Failed to give hub compass: " + e.getMessage());
        }
    }

    public Location getHubLocation() {
        return hubLocation;
    }

    public void setHubLocation(Location location) {
        this.hubLocation = location;
    }
}