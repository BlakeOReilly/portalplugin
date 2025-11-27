package com.blake.portalplugin.holograms;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;

import java.util.*;

public class HologramManager {

    private final PortalPlugin plugin;
    private final Map<String, List<ArmorStand>> holograms = new HashMap<>();

    public HologramManager(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearAll() {
        for (List<ArmorStand> list : holograms.values()) {
            list.forEach(ArmorStand::remove);
        }
        holograms.clear();
    }

    public void remove(String id) {
        List<ArmorStand> list = holograms.remove(id);
        if (list != null) list.forEach(ArmorStand::remove);
    }

    public void create(String id, Location loc, List<String> lines) {
        remove(id);

        List<ArmorStand> created = new ArrayList<>();
        Location base = loc.clone();

        for (String line : lines) {
            ArmorStand as = base.getWorld().spawn(base, ArmorStand.class, a -> {
                a.setVisible(false);
                a.setGravity(false);
                a.setMarker(true);
                a.setCustomNameVisible(true);
                a.setCustomName(line);
            });

            created.add(as);
            base.subtract(0, 0.25, 0);
        }

        holograms.put(id, created);
    }

    // ---------------------------------------------------------------------
    // AUTO UPDATE (called after wins change)
    // ---------------------------------------------------------------------
    public void updateAll(StatsManager statsManager) {
        for (String id : new HashSet<>(holograms.keySet())) {
            updateLeaderboard(id, statsManager);
        }
    }

    public void updateLeaderboard(String id, StatsManager statsManager) {
        if (!id.contains("_")) return;

        String[] split = id.split("_", 2);
        String gamemode = split[0];
        String type = split[1];

        if (!type.equalsIgnoreCase("wins")) return;

        List<String> lines = statsManager.getTopTenWinsSync(gamemode);
        List<ArmorStand> old = holograms.get(id);
        if (old == null || old.isEmpty()) return;

        Location loc = old.get(0).getLocation().clone().add(0, 0.25 * old.size(), 0);

        remove(id);
        create(id, loc, lines);

        // Also update lines in config so they reflect the latest state
        FileConfiguration cfg = plugin.getConfig();
        String base = "holograms." + id;
        cfg.set(base + ".lines", lines);
        plugin.saveConfig();
    }

    // Convenience for /createscoreboard
    public void createLeaderboard(String id, Location loc, List<String> lines) {
        create(id, loc, lines);
    }

    // ---------------------------------------------------------------------
    // PERSISTENCE SUPPORT
    // ---------------------------------------------------------------------

    /**
     * Called on server startup to rebuild holograms from config.
     * It will:
     *  - Read the stored world/x/y/z
     *  - Rebuild lines using StatsManager if possible (for type=wins)
     *  - Spawn the ArmorStands in the world
     */
    public void loadPersistentHolograms(StatsManager statsManager) {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection root = cfg.getConfigurationSection("holograms");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            String worldName = sec.getString("world");
            double x = sec.getDouble("x");
            double y = sec.getDouble("y");
            double z = sec.getDouble("z");

            if (worldName == null) continue;
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Location loc = new Location(world, x, y, z);

            String gamemode = sec.getString("gamemode");
            String type = sec.getString("type");

            List<String> lines;

            // If this is a known "wins" leaderboard, regenerate lines from DB
            if (gamemode != null && type != null && type.equalsIgnoreCase("wins")) {
                lines = statsManager.getTopTenWinsSync(gamemode);
                if (lines == null || lines.isEmpty()) {
                    lines = new ArrayList<>();
                    lines.add("§eTop Wins – §b" + gamemode);
                    lines.add("§7No data found.");
                }
            } else {
                // Fallback to stored lines if present
                List<String> stored = sec.getStringList("lines");
                if (stored == null || stored.isEmpty()) {
                    lines = Collections.singletonList("§7No data");
                } else {
                    lines = stored;
                }
            }

            create(id, loc, lines);
        }
    }
}
