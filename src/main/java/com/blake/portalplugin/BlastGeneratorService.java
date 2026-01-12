package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;

public class BlastGeneratorService {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;

    private BukkitTask task;

    private String cachedMapKey = null;
    private List<Location> cachedGenerators = new ArrayList<>();

    public BlastGeneratorService(PortalPlugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
    }

    public void start() {
        stop();

        // Run every 40 seconds, first run after 40 seconds
        this.task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tick,
                40L * 20L,
                40L * 20L
        );
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
        cachedMapKey = null;
        cachedGenerators = new ArrayList<>();
    }

    private void tick() {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) {
            cachedMapKey = null;
            cachedGenerators = new ArrayList<>();
            return;
        }

        BlastMap map = bm.getActiveMap();
        if (map == null) return;

        // Key: map name + world name (based on first spawn world)
        World w = getMapWorld(map);
        if (w == null) return;

        String key = safe(map.getName()) + "|" + w.getName();
        if (!key.equals(cachedMapKey) || cachedGenerators.isEmpty()) {
            cachedMapKey = key;
            cachedGenerators = discoverGenerators(map, w);

            if (cachedGenerators.size() != 4) {
                plugin.getLogger().warning("[PortalPlugin] BLAST generators found: " + cachedGenerators.size()
                        + " (expected 4). Map=" + safe(map.getName()) + " World=" + w.getName());
            } else {
                plugin.getLogger().info("[PortalPlugin] BLAST generators ready (4). Map=" + safe(map.getName()));
            }
        }

        if (cachedGenerators.isEmpty()) return;

        for (Location genBlockLoc : cachedGenerators) {
            if (genBlockLoc == null || genBlockLoc.getWorld() == null) continue;

            // Don’t force-load chunks
            int cx = genBlockLoc.getBlockX() >> 4;
            int cz = genBlockLoc.getBlockZ() >> 4;
            if (!genBlockLoc.getWorld().isChunkLoaded(cx, cz)) continue;

            Location dropLoc = genBlockLoc.clone().add(0.5, 1.10, 0.5);

            if (hasAnyBlasterItemOnGenerator(dropLoc)) continue;

            ItemStack big = BlastItems.createBigBlaster(plugin);
            if (big == null || big.getType().isAir()) continue;

            Item itemEnt = genBlockLoc.getWorld().dropItem(dropLoc, big);
            if (itemEnt != null) {
                // Keep it sitting on the generator
                try { itemEnt.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
                tryInvokeBoolean(itemEnt, "setGravity", false);
                tryInvokeBoolean(itemEnt, "setUnlimitedLifetime", true);
            }
        }
    }

    private boolean hasAnyBlasterItemOnGenerator(Location center) {
        World w = center.getWorld();
        if (w == null) return false;

        Collection<Entity> ents = w.getNearbyEntities(center, 0.65, 0.65, 0.65, e -> e instanceof Item);
        for (Entity e : ents) {
            if (!(e instanceof Item it)) continue;
            ItemStack stack = it.getItemStack();
            if (BlastItems.isBasicBlaster(plugin, stack) || BlastItems.isBigBlaster(plugin, stack)) {
                return true;
            }
        }
        return false;
    }

    private List<Location> discoverGenerators(BlastMap map, World world) {
        List<Location> spawns = collectLikelySpawnLocations(map);
        if (spawns.isEmpty()) return new ArrayList<>();

        // Use spawn bounds with padding
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        double sumY = 0;
        for (Location l : spawns) {
            minX = Math.min(minX, l.getBlockX());
            maxX = Math.max(maxX, l.getBlockX());
            minY = Math.min(minY, l.getBlockY());
            maxY = Math.max(maxY, l.getBlockY());
            minZ = Math.min(minZ, l.getBlockZ());
            maxZ = Math.max(maxZ, l.getBlockZ());
            sumY += l.getY();
        }

        int pad = 30;
        int xMin = minX - pad;
        int xMax = maxX + pad;
        int zMin = minZ - pad;
        int zMax = maxZ + pad;

        int avgY = (int) Math.round(sumY / Math.max(1, spawns.size()));
        int yMin = avgY - 20;
        int yMax = avgY + 20;

        // Cap scan size (avoid extreme maps)
        int xSpan = xMax - xMin + 1;
        int zSpan = zMax - zMin + 1;
        int ySpan = yMax - yMin + 1;
        long volume = (long) xSpan * (long) zSpan * (long) ySpan;
        if (volume > 2_000_000L) {
            // tighten if huge
            pad = 18;
            xMin = minX - pad;
            xMax = maxX + pad;
            zMin = minZ - pad;
            zMax = maxZ + pad;
            xSpan = xMax - xMin + 1;
            zSpan = zMax - zMin + 1;
            volume = (long) xSpan * (long) zSpan * (long) ySpan;
            if (volume > 2_000_000L) {
                // last resort: smaller Y window
                yMin = avgY - 12;
                yMax = avgY + 12;
            }
        }

        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double centerY = avgY;

        List<Location> found = new ArrayList<>();

        int cMinX = xMin >> 4;
        int cMaxX = xMax >> 4;
        int cMinZ = zMin >> 4;
        int cMaxZ = zMax >> 4;

        for (int cx = cMinX; cx <= cMaxX; cx++) {
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;

                int bxStart = Math.max(xMin, cx << 4);
                int bxEnd = Math.min(xMax, (cx << 4) + 15);
                int bzStart = Math.max(zMin, cz << 4);
                int bzEnd = Math.min(zMax, (cz << 4) + 15);

                for (int x = bxStart; x <= bxEnd; x++) {
                    for (int z = bzStart; z <= bzEnd; z++) {
                        for (int y = yMin; y <= yMax; y++) {
                            if (world.getBlockAt(x, y, z).getType() == Material.REDSTONE_BLOCK) {
                                found.add(new Location(world, x, y, z));
                                if (found.size() >= 32) break;
                            }
                        }
                        if (found.size() >= 32) break;
                    }
                    if (found.size() >= 32) break;
                }
                if (found.size() >= 32) break;
            }
            if (found.size() >= 32) break;
        }

        if (found.isEmpty()) return new ArrayList<>();

        // Sort by closeness to spawn-center and take first 4
        found.sort(Comparator.comparingDouble(l -> dist2(l, centerX, centerY, centerZ)));

        List<Location> result = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (Location l : found) {
            String k = l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
            if (dedupe.add(k)) result.add(l);
            if (result.size() >= 4) break;
        }
        return result;
    }

    private List<Location> collectLikelySpawnLocations(BlastMap map) {
        List<Location> out = new ArrayList<>();

        // Typical: 4 spawns per team
        for (BlastTeam t : BlastTeam.values()) {
            for (int i = 0; i < 4; i++) {
                try {
                    Location l = map.getSpawnFor(t, i);
                    if (l != null && l.getWorld() != null) out.add(l);
                } catch (Throwable ignored) {}
            }
        }

        return out;
    }

    private World getMapWorld(BlastMap map) {
        // Prefer a world from spawns
        try {
            for (BlastTeam t : BlastTeam.values()) {
                for (int i = 0; i < 4; i++) {
                    Location l = map.getSpawnFor(t, i);
                    if (l != null && l.getWorld() != null) return l.getWorld();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private double dist2(Location l, double cx, double cy, double cz) {
        double dx = l.getX() - cx;
        double dy = l.getY() - cy;
        double dz = l.getZ() - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void tryInvokeBoolean(Object target, String methodName, boolean arg) {
        if (target == null || methodName == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, boolean.class);
            m.invoke(target, arg);
        } catch (Throwable ignored) {}
    }
}
