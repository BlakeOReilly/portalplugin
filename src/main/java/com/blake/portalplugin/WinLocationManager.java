package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class WinLocationManager {

    private final PortalPlugin plugin;
    private Location winLocation;

    public WinLocationManager(PortalPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** Load win location from config */
    private void load() {
        FileConfiguration cfg = plugin.getConfig();

        String worldName = cfg.getString("win-location.world", null);
        if (worldName == null) {
            winLocation = null;
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            winLocation = null;
            return;
        }

        double x = cfg.getDouble("win-location.x");
        double y = cfg.getDouble("win-location.y");
        double z = cfg.getDouble("win-location.z");
        float yaw = (float) cfg.getDouble("win-location.yaw", 0f);
        float pitch = (float) cfg.getDouble("win-location.pitch", 0f);

        winLocation = new Location(world, x, y, z, yaw, pitch);
    }

    /** Save win location to config */
    public void setWinLocation(Location loc) {
        this.winLocation = loc;

        FileConfiguration cfg = plugin.getConfig();
        cfg.set("win-location.world", loc.getWorld().getName());
        cfg.set("win-location.x", loc.getX());
        cfg.set("win-location.y", loc.getY());
        cfg.set("win-location.z", loc.getZ());
        cfg.set("win-location.yaw", loc.getYaw());
        cfg.set("win-location.pitch", loc.getPitch());
        plugin.saveConfig();
    }

    public Location getWinLocation() {
        return winLocation;
    }

    public boolean hasWinLocation() {
        return winLocation != null;
    }
}
