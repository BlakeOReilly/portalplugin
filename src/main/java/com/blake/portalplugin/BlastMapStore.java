package com.blake.portalplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BlastMapStore {

    private final Plugin plugin;

    private final File file;
    private FileConfiguration config;

    private final Map<String, BlastMap> maps = new LinkedHashMap<>();

    public BlastMapStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "blast-maps.yml");
        ensureFile();
        reload();
    }

    private void ensureFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {}
        }

        // ensure region folder exists
        File regionDir = getRegionFolder();
        if (!regionDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            regionDir.mkdirs();
        }
    }

    public File getRegionFolder() {
        return new File(plugin.getDataFolder(), "blast-regions");
    }

    /**
     * Default snapshot file for a given map name.
     */
    public File getRegionFileForMapName(String mapName) {
        String safe = (mapName == null ? "unknown" : mapName.trim().toLowerCase());
        return new File(getRegionFolder(), safe + ".brs");
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        maps.clear();

        if (!config.contains("maps")) return;

        var sec = config.getConfigurationSection("maps");
        if (sec == null) return;

        for (String mapName : sec.getKeys(false)) {
            var mapSec = sec.getConfigurationSection(mapName);
            BlastMap map = BlastMap.loadFromConfig(mapName, mapSec);
            maps.put(map.getName(), map);
        }
    }

    public void save() {
        config.set("maps", null);

        for (BlastMap map : maps.values()) {
            var sec = config.createSection("maps." + map.getName());
            map.saveToConfig(sec);
        }

        try {
            config.save(file);
        } catch (IOException ignored) {}
    }

    public List<String> listMapNames() {
        return new ArrayList<>(maps.keySet());
    }

    public BlastMap getMap(String name) {
        if (name == null) return null;
        return maps.get(name.trim().toLowerCase());
    }

    public void putMap(BlastMap map) {
        if (map == null) return;
        maps.put(map.getName(), map);
        save();
    }

    public boolean removeMap(String name) {
        if (name == null) return false;
        BlastMap removed = maps.remove(name.trim().toLowerCase());
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public BlastMap getFirstMapOrNull() {
        for (BlastMap m : maps.values()) return m;
        return null;
    }
}
