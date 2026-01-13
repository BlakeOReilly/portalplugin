package com.blake.portalplugin;

import org.bukkit.Location;

public class BlastSpawnProtectionRegion {

    private final String worldName;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public BlastSpawnProtectionRegion(String worldName, double x1, double y1, double z1, double x2, double y2, double z2) {
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public String getWorldName() {
        return worldName;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (worldName == null || !worldName.equals(location.getWorld().getName())) return false;

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
