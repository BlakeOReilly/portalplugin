package com.blake.portalplugin.worldedit;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SelectionManager {

    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();

    public void setPos1(UUID uuid, Location loc) {
        if (uuid == null || loc == null) return;
        pos1.put(uuid, clampToBlock(loc));
    }

    public void setPos2(UUID uuid, Location loc) {
        if (uuid == null || loc == null) return;
        pos2.put(uuid, clampToBlock(loc));
    }

    public Location getPos1(UUID uuid) { return pos1.get(uuid); }
    public Location getPos2(UUID uuid) { return pos2.get(uuid); }

    public void clear(UUID uuid) {
        pos1.remove(uuid);
        pos2.remove(uuid);
    }

    public boolean hasCompleteSelection(UUID uuid) {
        return pos1.containsKey(uuid) && pos2.containsKey(uuid);
    }

    public boolean sameWorld(UUID uuid) {
        Location a = pos1.get(uuid);
        Location b = pos2.get(uuid);
        if (a == null || b == null) return false;
        World wa = a.getWorld();
        World wb = b.getWorld();
        return wa != null && wb != null && wa.getUID().equals(wb.getUID());
    }

    public long getVolume(UUID uuid) {
        Location a = pos1.get(uuid);
        Location b = pos2.get(uuid);
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return 0;

        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());

        long dx = (long) (maxX - minX + 1);
        long dy = (long) (maxY - minY + 1);
        long dz = (long) (maxZ - minZ + 1);
        return dx * dy * dz;
    }

    private Location clampToBlock(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }
}