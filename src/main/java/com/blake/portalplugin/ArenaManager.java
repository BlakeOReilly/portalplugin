// src/main/java/com/blake/portalplugin/arenas/ArenaManager.java
package com.blake.portalplugin.arenas;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ArenaManager {

    private final JavaPlugin plugin;
    private final Map<String, Arena> arenas = new HashMap<>();

    private File arenaFile;
    private FileConfiguration arenaConfig;

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadArenaFile();
        loadArenasFromFile();
    }

    /* ==========================================================
       LOAD arena-data.yml
       ========================================================== */
    private void loadArenaFile() {
        arenaFile = new File(plugin.getDataFolder(), "arena-data.yml");

        if (!plugin.getDataFolder().exists())
            plugin.getDataFolder().mkdirs();

        if (!arenaFile.exists()) {
            try {
                arenaFile.createNewFile();
            } catch (IOException ignored) {}
        }

        arenaConfig = YamlConfiguration.loadConfiguration(arenaFile);
    }

    /* ==========================================================
       SAVE arenas INCLUDING resetBlocks
       ========================================================== */
    public void saveArenasToFile() {

        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.getName();

            arenaConfig.set(base + ".inUse", arena.isInUse());
            arenaConfig.set(base + ".started", arena.hasStarted());
            arenaConfig.set(base + ".maxPlayers", arena.getMaxPlayers());

            /* ---------- Spawn Points ---------- */
            List<String> spawnStrings = new ArrayList<>();
            for (Location loc : arena.getSpawnPoints()) {
                if (loc == null || loc.getWorld() == null) continue;
                spawnStrings.add(loc.getWorld().getName() + ","
                        + loc.getX() + "," + loc.getY() + "," + loc.getZ());
            }
            arenaConfig.set(base + ".spawns", spawnStrings);

            /* ---------- Players ---------- */
            List<String> players = new ArrayList<>();
            for (UUID uuid : arena.getPlayers())
                players.add(uuid.toString());
            arenaConfig.set(base + ".players", players);

            /* ---------- RESET BLOCKS ---------- */
            Map<Material, List<Location>> resetMap = arena.getResetBlocks();
            Map<String, List<String>> yamlMap = new LinkedHashMap<>();

            for (Material m : resetMap.keySet()) {
                List<String> list = new ArrayList<>();
                for (Location l : resetMap.get(m)) {
                    if (l == null || l.getWorld() == null) continue;
                    list.add(l.getWorld().getName() + ","
                            + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
                }
                yamlMap.put(m.name(), list);
            }

            arenaConfig.set(base + ".resetBlocks", yamlMap);
        }

        try {
            arenaConfig.save(arenaFile);
        } catch (IOException ignored) {}
    }

    /* ==========================================================
       LOAD arenas INCLUDING resetBlocks
       ========================================================== */
    private void loadArenasFromFile() {
        if (!arenaConfig.contains("arenas")) return;

        for (String name : arenaConfig.getConfigurationSection("arenas").getKeys(false)) {

            Arena arena = new Arena(name);
            String base = "arenas." + name;

            arena.setInUse(arenaConfig.getBoolean(base + ".inUse", false));
            arena.setStarted(arenaConfig.getBoolean(base + ".started", false));
            arena.setMaxPlayers(arenaConfig.getInt(base + ".maxPlayers", 100));

            /* ---------- Load Spawns ---------- */
            for (String s : arenaConfig.getStringList(base + ".spawns")) {
                String[] split = s.split(",");
                if (split.length < 4) continue;

                World w = Bukkit.getWorld(split[0]);
                if (w == null) continue;

                arena.addSpawn(new Location(
                        w,
                        Double.parseDouble(split[1]),
                        Double.parseDouble(split[2]),
                        Double.parseDouble(split[3])
                ));
            }

            /* ---------- Load Players ---------- */
            for (String uuid : arenaConfig.getStringList(base + ".players")) {
                try {
                    arena.getPlayers().add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {}
            }

            /* ---------- LOAD RESET BLOCKS ---------- */
            if (arenaConfig.contains(base + ".resetBlocks")) {

                for (String matName :
                        arenaConfig.getConfigurationSection(base + ".resetBlocks").getKeys(false)) {

                    Material mat = Material.matchMaterial(matName);
                    if (mat == null) continue;

                    for (String entry :
                            arenaConfig.getStringList(base + ".resetBlocks." + matName)) {

                        String[] sp = entry.split(",");
                        if (sp.length < 4) continue;

                        World w = Bukkit.getWorld(sp[0]);
                        if (w == null) continue;

                        Location loc = new Location(
                                w,
                                Integer.parseInt(sp[1]),
                                Integer.parseInt(sp[2]),
                                Integer.parseInt(sp[3])
                        );

                        arena.addResetBlock(mat, loc);
                    }
                }
            }

            arenas.put(name.toLowerCase(), arena);
        }
    }

    /* ==========================================================
       -------------  SAVE RESET BLOCK DATA  ---------------------
       ========================================================== */
    public int saveArenaResetBlocks(Arena arena, Material material, Location c1, Location c2) {

        arena.clearResetBlocks(material);

        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int maxX = Math.max(c1.getBlockX(), c2.getBlockX());

        int minY = Math.min(c1.getBlockY(), c2.getBlockY());
        int maxY = Math.max(c1.getBlockY(), c2.getBlockY());

        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());

        World world = c1.getWorld();
        int count = 0;

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {

                    Location l = new Location(world, x, y, z);

                    if (l.getBlock().getType() == material) {
                        arena.addResetBlock(material, l);
                        count++;
                    }
                }

        return count;
    }

    /* ==========================================================
       -------------  RESTORE ARENA BLOCKS  ----------------------
       ========================================================== */
    public int resetArenaBlocks(Arena arena) {

        if (arena == null) return 0;

        plugin.getLogger().info("[PortalPlugin] resetArenaBlocks() called for arena='"
                + arena.getName() + "' started=" + arena.hasStarted() + " inUse=" + arena.isInUse()
                + " players=" + arena.getPlayers().size()
                + " spawns=" + arena.getSpawnPoints().size()
                + " resetBlocksKeys=" + arena.getResetBlocks().keySet().size());

        int changed = 0;

        for (Map.Entry<Material, List<Location>> entry : arena.getResetBlocks().entrySet()) {
            Material mat = entry.getKey();

            for (Location loc : entry.getValue()) {
                if (loc == null || loc.getWorld() == null) continue;
                loc.getBlock().setType(mat, false);
                changed++;
            }
        }

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' block reset changed=" + changed);

        /*
         * IMPORTANT:
         * Death drops often spawn at the end of the tick / next tick.
         * So we run cleanup immediately AND run delayed passes to prove what exists when.
         */
        runArenaItemCleanupPass(arena, "immediate");

        long delay1 = plugin.getConfig().getLong("arena-cleanup.delayTicks1", 2L);
        long delay2 = plugin.getConfig().getLong("arena-cleanup.delayTicks2", 20L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> runArenaItemCleanupPass(arena, "delayed+" + delay1), delay1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> runArenaItemCleanupPass(arena, "delayed+" + delay2), delay2);

        arena.setStarted(false);
        arena.setInUse(false);
        arena.clearCountdown();
        arena.setAssignedGame(null);

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' reset complete.");

        return changed;
    }

    /* ==========================================================
       -------------  CLEANUP PASS WRAPPER (DEBUG)  --------------
       ========================================================== */
    private void runArenaItemCleanupPass(Arena arena, String passName) {
        if (arena == null) return;

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' cleanup pass='" + passName + "' starting...");

        // Determine worlds that matter for this arena
        Set<World> worlds = new LinkedHashSet<>();
        for (List<Location> list : arena.getResetBlocks().values()) {
            for (Location l : list) {
                if (l != null && l.getWorld() != null) worlds.add(l.getWorld());
            }
        }
        for (Location l : arena.getSpawnPoints()) {
            if (l != null && l.getWorld() != null) worlds.add(l.getWorld());
        }

        if (worlds.isEmpty()) {
            plugin.getLogger().warning("[PortalPlugin] Arena '" + arena.getName() + "' cleanup pass='" + passName
                    + "': no worlds found from reset blocks or spawns.");
        } else {
            for (World w : worlds) {
                int totalItems = w.getEntitiesByClass(Item.class).size();
                plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' pass='" + passName
                        + "' world='" + w.getName() + "' totalItemEntitiesBefore=" + totalItems);
            }
        }

        int removedItems = clearDroppedItemsInArena(arena);

        if (!worlds.isEmpty()) {
            for (World w : worlds) {
                int totalItemsAfter = w.getEntitiesByClass(Item.class).size();
                plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' pass='" + passName
                        + "' world='" + w.getName() + "' totalItemEntitiesAfter=" + totalItemsAfter);
            }
        }

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                + "' cleanup pass='" + passName + "' removed=" + removedItems);

        // Log a sample of any remaining item entities STILL inside our computed boxes (if any).
        logSampleRemainingItemsInsideBoxes(arena, passName, 10);

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' cleanup pass='" + passName + "' finished.");
    }

    /* ==========================================================
       -------------  ITEM CLEANUP (ARENA END)  ------------------
       ========================================================== */
    private int clearDroppedItemsInArena(Arena arena) {
        if (arena == null) {
            plugin.getLogger().warning("[PortalPlugin] clearDroppedItemsInArena called with arena=null");
            return 0;
        }

        boolean enabled = plugin.getConfig().getBoolean("arena-cleanup.enabled", true);
        if (!enabled) {
            plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                    + "' cleanup skipped: arena-cleanup.enabled=false");
            return 0;
        }

        // Inferred bounds padding
        int padding = plugin.getConfig().getInt("arena-cleanup.padding", 6);
        int yDown = plugin.getConfig().getInt("arena-cleanup.yPaddingDown", 6);
        int yUp = plugin.getConfig().getInt("arena-cleanup.yPaddingUp", 24);

        // Extra: cleanup around spawns by radius
        int spawnRadius = plugin.getConfig().getInt("arena-cleanup.spawnRadius", 80);
        int spawnYDown = plugin.getConfig().getInt("arena-cleanup.spawnRadiusYDown", 30);
        int spawnYUp = plugin.getConfig().getInt("arena-cleanup.spawnRadiusYUp", 60);

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                + "' cleanup config: padding=" + padding + " yDown=" + yDown + " yUp=" + yUp
                + " spawnRadius=" + spawnRadius + " spawnYDown=" + spawnYDown + " spawnYUp=" + spawnYUp);

        // Build per-world bounds based on resetBlocks + spawn points
        Map<World, Bounds> boundsByWorld = new HashMap<>();

        int resetLocs = 0;
        for (List<Location> list : arena.getResetBlocks().values()) {
            for (Location l : list) {
                if (l == null || l.getWorld() == null) continue;
                boundsByWorld.computeIfAbsent(l.getWorld(), w -> new Bounds())
                        .include(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                resetLocs++;
            }
        }

        int spawnLocs = 0;
        for (Location l : arena.getSpawnPoints()) {
            if (l == null || l.getWorld() == null) continue;
            boundsByWorld.computeIfAbsent(l.getWorld(), w -> new Bounds())
                    .include(l.getBlockX(), l.getBlockY(), l.getBlockZ());
            spawnLocs++;
        }

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                + "' cleanup inputs: resetBlockLocations=" + resetLocs
                + " spawnLocations=" + spawnLocs
                + " worldsConsidered=" + boundsByWorld.size());

        int removed = 0;

        // 1) Remove items inside inferred bounds
        if (boundsByWorld.isEmpty()) {
            plugin.getLogger().warning("[PortalPlugin] Arena '" + arena.getName()
                    + "' cleanup: boundsByWorld is empty (no reset blocks + no spawns?)");
        } else {
            for (Map.Entry<World, Bounds> e : boundsByWorld.entrySet()) {
                World world = e.getKey();
                Bounds b = e.getValue();

                if (!b.isValid()) {
                    plugin.getLogger().warning("[PortalPlugin] Arena '" + arena.getName()
                            + "' cleanup: invalid bounds for world '" + world.getName() + "'");
                    continue;
                }

                int minX = b.minX - padding;
                int maxX = b.maxX + padding;
                int minY = b.minY - yDown;
                int maxY = b.maxY + yUp;
                int minZ = b.minZ - padding;
                int maxZ = b.maxZ + padding;

                plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                        + "' inferred-clear box world='" + world.getName()
                        + "' X[" + minX + ".." + maxX + "]"
                        + " Y[" + minY + ".." + maxY + "]"
                        + " Z[" + minZ + ".." + maxZ + "]"
                        + " (rawBounds: X[" + b.minX + ".." + b.maxX + "]"
                        + " Y[" + b.minY + ".." + b.maxY + "]"
                        + " Z[" + b.minZ + ".." + b.maxZ + "])");

                int before = removed;

                for (Entity ent : world.getEntities()) {
                    if (!(ent instanceof Item)) continue;

                    Location el = ent.getLocation();
                    int x = el.getBlockX();
                    int y = el.getBlockY();
                    int z = el.getBlockZ();

                    if (x >= minX && x <= maxX
                            && y >= minY && y <= maxY
                            && z >= minZ && z <= maxZ) {
                        ent.remove();
                        removed++;
                    }
                }

                plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                        + "' inferred-clear removed=" + (removed - before)
                        + " in world='" + world.getName() + "'");
            }
        }

        // 2) Remove items around each spawn point (covers large arenas / sparse resetBlocks)
        int spawnBoxIndex = 0;
        for (Location spawn : arena.getSpawnPoints()) {
            if (spawn == null || spawn.getWorld() == null) continue;

            spawnBoxIndex++;

            BoundingBox box = BoundingBox.of(
                    spawn,
                    Math.max(1, spawnRadius),
                    Math.max(1, spawnYDown),
                    Math.max(1, spawnRadius)
            );

            // Expand upward if "up" is larger than "down"
            int extraUp = Math.max(0, spawnYUp - spawnYDown);
            if (extraUp > 0) {
                box = box.expand(0, extraUp, 0);
            }

            plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                    + "' spawn-clear box#" + spawnBoxIndex
                    + " world='" + spawn.getWorld().getName() + "'"
                    + " center=(" + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ() + ")"
                    + " min=(" + (int) Math.floor(box.getMinX()) + "," + (int) Math.floor(box.getMinY()) + "," + (int) Math.floor(box.getMinZ()) + ")"
                    + " max=(" + (int) Math.floor(box.getMaxX()) + "," + (int) Math.floor(box.getMaxY()) + "," + (int) Math.floor(box.getMaxZ()) + ")");

            int before = removed;

            for (Entity ent : spawn.getWorld().getNearbyEntities(box)) {
                if (!(ent instanceof Item)) continue;
                ent.remove();
                removed++;
            }

            plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                    + "' spawn-clear removed=" + (removed - before)
                    + " for box#" + spawnBoxIndex);
        }

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName()
                + "' cleanup finished. totalRemoved=" + removed);

        return removed;
    }

    /* ==========================================================
       -------------  DEBUG: WHAT REMAINS IN BOXES  --------------
       ========================================================== */
    private void logSampleRemainingItemsInsideBoxes(Arena arena, String passName, int maxSamples) {
        if (arena == null) return;

        // Build the same inferred bounds as cleanup uses
        Map<World, Bounds> boundsByWorld = new HashMap<>();

        for (List<Location> list : arena.getResetBlocks().values()) {
            for (Location l : list) {
                if (l == null || l.getWorld() == null) continue;
                boundsByWorld.computeIfAbsent(l.getWorld(), w -> new Bounds())
                        .include(l.getBlockX(), l.getBlockY(), l.getBlockZ());
            }
        }

        for (Location l : arena.getSpawnPoints()) {
            if (l == null || l.getWorld() == null) continue;
            boundsByWorld.computeIfAbsent(l.getWorld(), w -> new Bounds())
                    .include(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        }

        int padding = plugin.getConfig().getInt("arena-cleanup.padding", 6);
        int yDown = plugin.getConfig().getInt("arena-cleanup.yPaddingDown", 6);
        int yUp = plugin.getConfig().getInt("arena-cleanup.yPaddingUp", 24);

        int spawnRadius = plugin.getConfig().getInt("arena-cleanup.spawnRadius", 80);
        int spawnYDown = plugin.getConfig().getInt("arena-cleanup.spawnRadiusYDown", 30);
        int spawnYUp = plugin.getConfig().getInt("arena-cleanup.spawnRadiusYUp", 60);

        int samples = 0;

        // Check inferred boxes
        for (Map.Entry<World, Bounds> e : boundsByWorld.entrySet()) {
            World world = e.getKey();
            Bounds b = e.getValue();
            if (!b.isValid()) continue;

            int minX = b.minX - padding;
            int maxX = b.maxX + padding;
            int minY = b.minY - yDown;
            int maxY = b.maxY + yUp;
            int minZ = b.minZ - padding;
            int maxZ = b.maxZ + padding;

            for (Item item : world.getEntitiesByClass(Item.class)) {
                Location el = item.getLocation();
                int x = el.getBlockX();
                int y = el.getBlockY();
                int z = el.getBlockZ();

                if (x >= minX && x <= maxX
                        && y >= minY && y <= maxY
                        && z >= minZ && z <= maxZ) {
                    plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' pass='" + passName
                            + "' STILL-IN-INFERRED item=" + item.getItemStack().getType()
                            + " at (" + x + "," + y + "," + z + ") world='" + world.getName() + "'");
                    if (++samples >= maxSamples) return;
                }
            }
        }

        // Check spawn boxes
        int idx = 0;
        for (Location spawn : arena.getSpawnPoints()) {
            if (spawn == null || spawn.getWorld() == null) continue;
            idx++;

            BoundingBox box = BoundingBox.of(
                    spawn,
                    Math.max(1, spawnRadius),
                    Math.max(1, spawnYDown),
                    Math.max(1, spawnRadius)
            );

            int extraUp = Math.max(0, spawnYUp - spawnYDown);
            if (extraUp > 0) box = box.expand(0, extraUp, 0);

            for (Entity ent : spawn.getWorld().getNearbyEntities(box)) {
                if (!(ent instanceof Item item)) continue;
                Location el = item.getLocation();

                plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' pass='" + passName
                        + "' STILL-IN-SPAWNBOX#" + idx
                        + " item=" + item.getItemStack().getType()
                        + " at (" + el.getBlockX() + "," + el.getBlockY() + "," + el.getBlockZ()
                        + ") world='" + spawn.getWorld().getName() + "'");
                if (++samples >= maxSamples) return;
            }
        }

        plugin.getLogger().info("[PortalPlugin] Arena '" + arena.getName() + "' pass='" + passName
                + "' remaining-item sample: none found inside computed boxes.");
    }

    private static final class Bounds {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        void include(int x, int y, int z) {
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        boolean isValid() {
            return minX != Integer.MAX_VALUE
                    && minY != Integer.MAX_VALUE
                    && minZ != Integer.MAX_VALUE
                    && maxX != Integer.MIN_VALUE
                    && maxY != Integer.MIN_VALUE
                    && maxZ != Integer.MIN_VALUE;
        }
    }

    /* ==========================================================
       ---------- EXISTING PUBLIC API (UNCHANGED) ---------------
       ========================================================== */

    public boolean arenaExists(String name) { return arenas.containsKey(name.toLowerCase()); }

    public Arena getArena(String name) { return arenas.get(name.toLowerCase()); }

    public Arena createArena(String name) {
        Arena a = new Arena(name);
        arenas.put(name.toLowerCase(), a);
        return a;
    }

    public Collection<Arena> getAllArenas() { return arenas.values(); }

    public Arena getArenaPlayerIsIn(org.bukkit.entity.Player player) {
        UUID uuid = player.getUniqueId();
        for (Arena a : arenas.values())
            if (a.getPlayers().contains(uuid)) return a;
        return null;
    }
}
