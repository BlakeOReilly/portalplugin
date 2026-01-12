package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BlastDiamondSpawnerService {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;

    private final NamespacedKey spawnedDiamondKey;

    private BukkitRunnable task;
    private final Set<UUID> activeDiamondEntities = new HashSet<>();

    private static final int PERIOD_TICKS = 30 * 20;  // 30 seconds
    private static final int SPAWN_PER_WAVE = 5;
    private static final int MAX_ACTIVE = 20;

    // Used when region bounds aren't set; derived from spawns with padding.
    private static final int FALLBACK_PADDING_BLOCKS = 20;

    // If world border is huge (default), we won't use it as a safe bound.
    private static final double MAX_REASONABLE_BORDER_SIZE = 5000.0;

    // Avoid spamming logs
    private boolean warnedNoBounds = false;

    public BlastDiamondSpawnerService(PortalPlugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
        this.spawnedDiamondKey = new NamespacedKey(plugin, "blast_spawned_diamond");
    }

    public void start() {
        if (task != null) return;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tickSpawn();
            }
        };

        // start after 30s so maps/players are stable
        task.runTaskTimer(plugin, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
        clearAllActiveDiamonds();
    }

    public NamespacedKey getSpawnedDiamondKey() {
        return spawnedDiamondKey;
    }

    public boolean isTrackedSpawnedDiamond(Item itemEntity) {
        if (itemEntity == null) return false;
        try {
            Byte b = itemEntity.getPersistentDataContainer().get(spawnedDiamondKey, PersistentDataType.BYTE);
            return b != null && b == (byte) 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void unregister(Item itemEntity) {
        if (itemEntity == null) return;
        activeDiamondEntities.remove(itemEntity.getUniqueId());
    }

    public void clearAllActiveDiamonds() {
        for (UUID id : new HashSet<>(activeDiamondEntities)) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Item it) {
                try { it.remove(); } catch (Throwable ignored) {}
            }
        }
        activeDiamondEntities.clear();
    }

    private void tickSpawn() {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();

        // If BLAST isn't running, keep the world clean of previously spawned diamonds
        if (bm == null || !bm.isInProgress()) {
            warnedNoBounds = false;
            pruneInvalid();
            clearAllActiveDiamonds();
            return;
        }

        pruneInvalid();

        int active = activeDiamondEntities.size();
        int capacity = Math.max(0, MAX_ACTIVE - active);
        if (capacity <= 0) return;

        int toSpawn = Math.min(SPAWN_PER_WAVE, capacity);

        BlastMap map = bm.getActiveMap();
        if (map == null) return;

        World world = resolveWorld(map);
        if (world == null) return;

        Bounds bounds = resolveBounds(map, world);
        if (bounds == null) {
            if (!warnedNoBounds) {
                warnedNoBounds = true;
                plugin.getLogger().warning("[BLAST] Diamond spawns skipped: could not resolve safe bounds. Set BlastMap region.min/max or ensure team spawns are configured.");
            }
            return;
        }

        for (int i = 0; i < toSpawn; i++) {
            Location loc = findSolidGroundSpawn(world, bounds, 30);
            if (loc == null) continue;

            Item dropped;
            try {
                dropped = world.dropItem(loc, new ItemStack(Material.DIAMOND, 1));
            } catch (Throwable ignored) {
                continue;
            }

            try {
                dropped.setVelocity(dropped.getVelocity().zero());
            } catch (Throwable ignored) {}

            try {
                dropped.setPickupDelay(0);
            } catch (Throwable ignored) {}

            try {
                dropped.getPersistentDataContainer().set(spawnedDiamondKey, PersistentDataType.BYTE, (byte) 1);
            } catch (Throwable ignored) {}

            activeDiamondEntities.add(dropped.getUniqueId());
        }
    }

    private void pruneInvalid() {
        Iterator<UUID> it = activeDiamondEntities.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Item item)) { it.remove(); continue; }
            if (!item.isValid() || item.isDead()) { it.remove(); continue; }

            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType() != Material.DIAMOND) { it.remove(); continue; }

            if (!isTrackedSpawnedDiamond(item)) { it.remove(); }
        }
    }

    private World resolveWorld(BlastMap map) {
        if (map == null) return null;

        if (map.getWorldName() != null) {
            World w = Bukkit.getWorld(map.getWorldName());
            if (w != null) return w;
        }

        // fallback: use any spawn's world
        for (BlastTeam t : BlastTeam.values()) {
            for (Location l : map.getSpawns(t)) {
                if (l != null && l.getWorld() != null) return l.getWorld();
            }
        }

        // fallback: paste location
        if (map.getPasteLocation() != null && map.getPasteLocation().getWorld() != null) {
            return map.getPasteLocation().getWorld();
        }

        return null;
    }

    private Bounds resolveBounds(BlastMap map, World world) {
        if (map == null || world == null) return null;

        Location rMin = map.getRegionMin();
        Location rMax = map.getRegionMax();

        if (rMin != null && rMax != null && rMin.getWorld() == world && rMax.getWorld() == world) {
            int minX = (int) Math.floor(Math.min(rMin.getX(), rMax.getX()));
            int maxX = (int) Math.floor(Math.max(rMin.getX(), rMax.getX()));
            int minY = (int) Math.floor(Math.min(rMin.getY(), rMax.getY()));
            int maxY = (int) Math.floor(Math.max(rMin.getY(), rMax.getY()));
            int minZ = (int) Math.floor(Math.min(rMin.getZ(), rMax.getZ()));
            int maxZ = (int) Math.floor(Math.max(rMin.getZ(), rMax.getZ()));

            return clampToWorld(world, new Bounds(minX, maxX, minY, maxY, minZ, maxZ));
        }

        // Fallback: derive bounds from all team spawns
        boolean foundAny = false;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlastTeam t : BlastTeam.values()) {
            for (Location l : map.getSpawns(t)) {
                if (l == null || l.getWorld() != world) continue;
                foundAny = true;
                int x = l.getBlockX();
                int z = l.getBlockZ();
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }

        if (foundAny) {
            minX -= FALLBACK_PADDING_BLOCKS;
            maxX += FALLBACK_PADDING_BLOCKS;
            minZ -= FALLBACK_PADDING_BLOCKS;
            maxZ += FALLBACK_PADDING_BLOCKS;

            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight() - 2;

            return clampToWorld(world, new Bounds(minX, maxX, minY, maxY, minZ, maxZ));
        }

        // Fallback: use world border if reasonably sized
        try {
            WorldBorder border = world.getWorldBorder();
            double size = border.getSize();
            if (size > 0 && size <= MAX_REASONABLE_BORDER_SIZE) {
                Location c = border.getCenter();
                int half = (int) Math.floor(size / 2.0);

                int bx1 = c.getBlockX() - half;
                int bx2 = c.getBlockX() + half;
                int bz1 = c.getBlockZ() - half;
                int bz2 = c.getBlockZ() + half;

                int minY = world.getMinHeight();
                int maxY = world.getMaxHeight() - 2;

                return clampToWorld(world, new Bounds(bx1, bx2, minY, maxY, bz1, bz2));
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private Bounds clampToWorld(World world, Bounds b) {
        if (world == null || b == null) return b;

        int minY = Math.max(world.getMinHeight(), b.minY);
        int maxY = Math.min(world.getMaxHeight() - 2, b.maxY);

        return new Bounds(b.minX, b.maxX, minY, maxY, b.minZ, b.maxZ);
    }

    private Location findSolidGroundSpawn(World world, Bounds b, int maxAttempts) {
        if (world == null || b == null) return null;

        int minX = Math.min(b.minX, b.maxX);
        int maxX = Math.max(b.minX, b.maxX);
        int minZ = Math.min(b.minZ, b.maxZ);
        int maxZ = Math.max(b.minZ, b.maxZ);

        int minY = Math.min(b.minY, b.maxY);
        int maxY = Math.max(b.minY, b.maxY);

        Random rng = java.util.concurrent.ThreadLocalRandom.current();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = rng.nextInt(minX, maxX + 1);
            int z = rng.nextInt(minZ, maxZ + 1);

            // Scan down within bounds to find "solid ground with air above"
            for (int y = maxY; y >= minY; y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (ground == null) continue;

                if (!ground.getType().isSolid()) continue;

                Block above = world.getBlockAt(x, y + 1, z);
                if (above == null) continue;
                if (!above.getType().isAir()) continue;

                return new Location(world, x + 0.5, y + 1.15, z + 0.5);
            }
        }

        return null;
    }

    private static final class Bounds {
        final int minX, maxX;
        final int minY, maxY;
        final int minZ, maxZ;

        Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}
