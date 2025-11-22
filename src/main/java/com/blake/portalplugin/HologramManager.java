package com.blake.portalplugin.holograms;

import com.blake.portalplugin.stats.StatsManager;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import java.util.*;

public class HologramManager {

    private final Map<String, List<ArmorStand>> holograms = new HashMap<>();

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
    }

    // Convenience for /createscoreboard
    public void createLeaderboard(String id, Location loc, List<String> lines) {
        create(id, loc, lines);
    }
}
