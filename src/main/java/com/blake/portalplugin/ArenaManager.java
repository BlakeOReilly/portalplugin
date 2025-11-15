package com.blake.portalplugin.arenas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    private void loadArenaFile() {
        arenaFile = new File(plugin.getDataFolder(), "arena-data.yml");

        if (!plugin.getDataFolder().exists())
            plugin.getDataFolder().mkdirs();

        if (!arenaFile.exists()) {
            try {
                arenaFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        arenaConfig = YamlConfiguration.loadConfiguration(arenaFile);
    }

    // --------------------------------------------------------------
    // SAVE ARENAS
    // --------------------------------------------------------------
    public void saveArenasToFile() {

        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.getName();

            arenaConfig.set(base + ".inUse", arena.isInUse());
            arenaConfig.set(base + ".started", arena.hasStarted());
            arenaConfig.set(base + ".maxPlayers", arena.getMaxPlayers());

            // save spawn points
            List<String> spawnStrings = new ArrayList<>();
            for (Location loc : arena.getSpawnPoints()) {
                spawnStrings.add(loc.getWorld().getName() + "," +
                        loc.getX() + "," + loc.getY() + "," + loc.getZ());
            }
            arenaConfig.set(base + ".spawns", spawnStrings);

            // save players
            List<String> playerUUIDs = new ArrayList<>();
            arena.getPlayers().forEach(uuid -> playerUUIDs.add(uuid.toString()));
            arenaConfig.set(base + ".players", playerUUIDs);
        }

        try {
            arenaConfig.save(arenaFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --------------------------------------------------------------
    // LOAD ARENAS
    // --------------------------------------------------------------
    private void loadArenasFromFile() {
        if (!arenaConfig.contains("arenas")) return;

        for (String name : arenaConfig.getConfigurationSection("arenas").getKeys(false)) {
            Arena arena = new Arena(name);
            String base = "arenas." + name;

            arena.setInUse(arenaConfig.getBoolean(base + ".inUse", false));
            arena.setStarted(arenaConfig.getBoolean(base + ".started", false));
            arena.setMaxPlayers(arenaConfig.getInt(base + ".maxPlayers", 100));

            // load spawns
            List<String> spawnStrings = arenaConfig.getStringList(base + ".spawns");
            for (String s : spawnStrings) {
                String[] split = s.split(",");
                if (split.length == 4) {
                    arena.addSpawn(new Location(
                            Bukkit.getWorld(split[0]),
                            Double.parseDouble(split[1]),
                            Double.parseDouble(split[2]),
                            Double.parseDouble(split[3])
                    ));
                }
            }

            // load players
            List<String> uuidStrings = arenaConfig.getStringList(base + ".players");
            for (String uuidString : uuidStrings) {
                try {
                    arena.getPlayers().add(UUID.fromString(uuidString));
                } catch (IllegalArgumentException ignored) {}
            }

            arenas.put(name.toLowerCase(), arena);
        }
    }

    // --------------------------------------------------------------
    // GETTERS
    // --------------------------------------------------------------

    public boolean arenaExists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Arena createArena(String name) {
        Arena arena = new Arena(name);
        arenas.put(name.toLowerCase(), arena);
        return arena;
    }

    public Collection<Arena> getAllArenas() {
        return arenas.values();
    }
}
