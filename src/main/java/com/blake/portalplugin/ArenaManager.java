package com.blake.portalplugin.arenas;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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
                arena.addSpawn(new Location(
                        Bukkit.getWorld(split[0]),
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
                        World w = Bukkit.getWorld(sp[0]);

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

        int changed = 0;

        for (Map.Entry<Material, List<Location>> entry :
                arena.getResetBlocks().entrySet()) {

            Material mat = entry.getKey();

            for (Location loc : entry.getValue()) {
                if (loc.getWorld() == null) continue;
                loc.getBlock().setType(mat, false);
                changed++;
            }
        }

        arena.setStarted(false);
        arena.setInUse(false);
        arena.clearCountdown();

        return changed;
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
