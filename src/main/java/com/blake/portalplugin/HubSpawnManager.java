package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class HubSpawnManager {

    private final Plugin plugin;
    private Location hubSpawn;

    public HubSpawnManager(Plugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    public Location getHubSpawn() {
        if (hubSpawn == null) {
            loadFromConfig();
        }
        return hubSpawn;
    }

    public void setHubSpawn(Location loc) {
        if (loc == null) return;

        this.hubSpawn = loc;

        FileConfiguration cfg = plugin.getConfig();
        cfg.set("hub-spawn.world", loc.getWorld() != null ? loc.getWorld().getName() : null);
        cfg.set("hub-spawn.x", loc.getX());
        cfg.set("hub-spawn.y", loc.getY());
        cfg.set("hub-spawn.z", loc.getZ());
        cfg.set("hub-spawn.yaw", loc.getYaw());
        cfg.set("hub-spawn.pitch", loc.getPitch());
        plugin.saveConfig();
    }

    private void loadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("hub-spawn.world", null);

        World world = null;
        if (worldName != null) {
            world = Bukkit.getWorld(worldName);
        }

        if (world != null) {
            double x = cfg.getDouble("hub-spawn.x", world.getSpawnLocation().getX());
            double y = cfg.getDouble("hub-spawn.y", world.getSpawnLocation().getY());
            double z = cfg.getDouble("hub-spawn.z", world.getSpawnLocation().getZ());
            float yaw = (float) cfg.getDouble("hub-spawn.yaw", world.getSpawnLocation().getYaw());
            float pitch = (float) cfg.getDouble("hub-spawn.pitch", world.getSpawnLocation().getPitch());
            hubSpawn = new Location(world, x, y, z, yaw, pitch);
        } else {
            // Fallback: main world spawn
            if (!Bukkit.getWorlds().isEmpty()) {
                hubSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            } else {
                hubSpawn = null;
            }
        }
    }
}
