// src/main/java/com/blake/portalplugin/BlastMiddleGeneratorService.java
package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;

public class BlastMiddleGeneratorService {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;

    private BukkitTask task;

    private String cachedMapKey = null;
    private Location cachedDiamond = null;

    private long lastDiamondScanAttemptMs = 0L;

    // Spawn once per minute boundary: 1,2,3... (minuteIndex = elapsedSeconds/60)
    private int lastSpawnMinuteIndex = 0;

    private final Random rng = new Random();

    public BlastMiddleGeneratorService(PortalPlugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
    }

    public void start() {
        stop();

        // Tick every second to align to minute boundaries via Blast secondsRemaining
        this.task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tick,
                20L,
                20L
        );
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
        cachedMapKey = null;
        cachedDiamond = null;
        lastDiamondScanAttemptMs = 0L;
        lastSpawnMinuteIndex = 0;
    }

    private void tick() {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) {
            cachedMapKey = null;
            cachedDiamond = null;
            lastDiamondScanAttemptMs = 0L;
            lastSpawnMinuteIndex = 0;
            return;
        }

        BlastMap map = bm.getActiveMap();
        if (map == null) return;

        World w = getMapWorld(map);
        if (w == null) return;

        String key = safe(map.getName()) + "|" + w.getName();
        if (!key.equals(cachedMapKey)) {
            cachedMapKey = key;
            cachedDiamond = null;
            lastDiamondScanAttemptMs = 0L;
            lastSpawnMinuteIndex = 0;
        }

        // Determine elapsed seconds since game start using the authoritative BLAST timer
        int remaining = bm.getSecondsRemaining();
        int elapsed = BlastMinigameManager.MAX_SECONDS - remaining;
        if (elapsed < 0) elapsed = 0;

        int minuteIndex = elapsed / 60; // 0 at start, 1 after 60s, 2 after 120s...
        if (minuteIndex < 1) return;

        // Only act once per minute boundary
        if (minuteIndex == lastSpawnMinuteIndex) return;

        // Discover the diamond block (throttle scanning to avoid heavy work if chunks aren't loaded yet)
        if (cachedDiamond == null) {
            long now = System.currentTimeMillis();
            if (now - lastDiamondScanAttemptMs < 10_000L) return;
            lastDiamondScanAttemptMs = now;

            cachedDiamond = discoverDiamondGenerator(map, w);
            if (cachedDiamond == null) {
                plugin.getLogger().warning("[PortalPlugin] BLAST middle generator diamond block not found (scan limited to loaded chunks). Map=" + safe(map.getName()));
                return;
            } else {
                plugin.getLogger().info("[PortalPlugin] BLAST middle generator found at "
                        + cachedDiamond.getWorld().getName() + " "
                        + cachedDiamond.getBlockX() + " " + cachedDiamond.getBlockY() + " " + cachedDiamond.getBlockZ()
                        + " (Map=" + safe(map.getName()) + ")");
            }
        }

        // Don’t force-load chunks
        int cx = cachedDiamond.getBlockX() >> 4;
        int cz = cachedDiamond.getBlockZ() >> 4;
        if (!w.isChunkLoaded(cx, cz)) return;

        // Replace item on the generator with a new random special blaster
        Location dropLoc = cachedDiamond.clone().add(0.5, 1.10, 0.5);

        removeItemsOnGenerator(dropLoc);

        ItemStack toSpawn = createRandomSpecialBlaster();
        if (toSpawn == null || toSpawn.getType().isAir()) return;

        Item it = w.dropItem(dropLoc, toSpawn);
        if (it != null) {
            try { it.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
            tryInvokeBoolean(it, "setGravity", false);
            tryInvokeBoolean(it, "setUnlimitedLifetime", true);
        }

        announceSpawn(toSpawn);

        lastSpawnMinuteIndex = minuteIndex;
    }

    private void announceSpawn(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return;
        String name = stack.getItemMeta().hasDisplayName() ? stack.getItemMeta().getDisplayName() : stack.getType().name();

        String msg = "§6[BLAST] Middle generator spawned: " + name + "§6.";

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            if (gameStateManager.getGameState(p) != GameState.BLAST) continue;
            p.sendMessage(msg);
        }
    }

    private void removeItemsOnGenerator(Location center) {
        World w = center.getWorld();
        if (w == null) return;

        Collection<Entity> ents = w.getNearbyEntities(center, 0.80, 0.80, 0.80, e -> e instanceof Item);
        for (Entity e : ents) {
            try { e.remove(); } catch (Throwable ignored) {}
        }
    }

    private ItemStack createRandomSpecialBlaster() {
        int r = rng.nextInt(3);

        // Prefer the direct methods (compile-time)
        try {
            return switch (r) {
                case 0 -> BlastItems.createStrikeBlaster(plugin);
                case 1 -> BlastItems.createScatterBlaster(plugin);
                default -> BlastItems.createRangeBlaster(plugin);
            };
        } catch (Throwable t) {
            // If something goes wrong, avoid breaking the game loop
            plugin.getLogger().warning("[PortalPlugin] Failed to create middle generator item: " + t.getClass().getSimpleName());
            return null;
        }
    }

    private Location discoverDiamondGenerator(BlastMap map, World world) {
        List<Location> spawns = collectLikelySpawnLocations(map);
        if (spawns.isEmpty()) return null;

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

        int pad = 40;
        int xMin = minX - pad;
        int xMax = maxX + pad;
        int zMin = minZ - pad;
        int zMax = maxZ + pad;

        int avgY = (int) Math.round(sumY / Math.max(1, spawns.size()));
        int yMin = avgY - 14;
        int yMax = avgY + 14;

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
                            if (world.getBlockAt(x, y, z).getType() == Material.DIAMOND_BLOCK) {
                                found.add(new Location(world, x, y, z));
                                if (found.size() >= 8) break;
                            }
                        }
                        if (found.size() >= 8) break;
                    }
                    if (found.size() >= 8) break;
                }
                if (found.size() >= 8) break;
            }
            if (found.size() >= 8) break;
        }

        if (found.isEmpty()) return null;

        found.sort(Comparator.comparingDouble(l -> dist2(l, centerX, centerY, centerZ)));
        return found.get(0);
    }

    private List<Location> collectLikelySpawnLocations(BlastMap map) {
        List<Location> out = new ArrayList<>();
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
