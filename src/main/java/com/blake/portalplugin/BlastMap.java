// BlastMap.java
package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class BlastMap {

    private final String name;
    private String worldName;

    // Optional schematic path (if you still want it)
    private String schematic;

    // Paste location (where regen starts placing blocks)
    private Location pasteLocation;

    // Saved region info (for display / debugging)
    private Location regionMin;
    private Location regionMax;

    // Saved region blocks relative to regionMin (dx/dy/dz)
    private List<BlastSavedBlock> savedBlocks = new ArrayList<>();

    // Optional ceiling Y cap for this map
    private Integer ceilingY;

    // 4 spawn points per team (up to 4 each)
    private final EnumMap<BlastTeam, List<Location>> spawns = new EnumMap<>(BlastTeam.class);

    // Start spawn (used for player join spawn per map)
    private Location startSpawn;

    public BlastMap(String name) {
        this.name = (name == null ? "unknown" : name.trim().toLowerCase());
        for (BlastTeam t : BlastTeam.values()) {
            spawns.put(t, new ArrayList<>());
        }
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = (worldName == null ? null : worldName.trim());
    }

    public String getSchematic() {
        return schematic;
    }

    public void setSchematic(String schematic) {
        if (schematic == null || schematic.isBlank()) {
            this.schematic = null;
        } else {
            this.schematic = schematic.trim();
        }
    }

    public Location getPasteLocation() {
        return pasteLocation;
    }

    public void setPasteLocation(Location pasteLocation) {
        this.pasteLocation = pasteLocation;
        if (pasteLocation != null && pasteLocation.getWorld() != null && worldName == null) {
            worldName = pasteLocation.getWorld().getName();
        }
    }

    // -------------------------
    // Region save/paste fields
    // -------------------------

    public Location getRegionMin() {
        return regionMin;
    }

    public void setRegionMin(Location regionMin) {
        this.regionMin = regionMin;
        if (regionMin != null && regionMin.getWorld() != null && worldName == null) {
            worldName = regionMin.getWorld().getName();
        }
    }

    public Location getRegionMax() {
        return regionMax;
    }

    public void setRegionMax(Location regionMax) {
        this.regionMax = regionMax;
        if (regionMax != null && regionMax.getWorld() != null && worldName == null) {
            worldName = regionMax.getWorld().getName();
        }
    }

    public List<BlastSavedBlock> getSavedBlocks() {
        return savedBlocks;
    }

    public void setSavedBlocks(List<BlastSavedBlock> savedBlocks) {
        this.savedBlocks = (savedBlocks == null) ? new ArrayList<>() : new ArrayList<>(savedBlocks);
    }

    public Integer getCeilingY() {
        return ceilingY;
    }

    public void setCeilingY(Integer ceilingY) {
        this.ceilingY = ceilingY;
    }

    public Location getStartSpawn() {
        return startSpawn;
    }

    public void setStartSpawn(Location startSpawn) {
        this.startSpawn = startSpawn;
        if (startSpawn != null && startSpawn.getWorld() != null && worldName == null) {
            worldName = startSpawn.getWorld().getName();
        }
    }

    // -------------------------
    // Spawns
    // -------------------------

    public List<Location> getSpawns(BlastTeam team) {
        return spawns.getOrDefault(team, new ArrayList<>());
    }

    public void clearSpawns(BlastTeam team) {
        List<Location> list = spawns.get(team);
        if (list != null) list.clear();
    }

    public void addSpawn(BlastTeam team, Location loc) {
        if (team == null || loc == null || loc.getWorld() == null) return;

        List<Location> list = spawns.computeIfAbsent(team, k -> new ArrayList<>());
        if (list.size() >= 4) return; // capped at 4 per team per spec
        list.add(loc);

        if (worldName == null) worldName = loc.getWorld().getName();
    }

    /**
     * Sets spawn by index 0-3 (used by /blastmap setspawn <1-4>).
     */
    public void setSpawn(BlastTeam team, int index, Location loc) {
        if (team == null || loc == null || loc.getWorld() == null) return;
        if (index < 0 || index > 3) return;

        List<Location> list = spawns.computeIfAbsent(team, k -> new ArrayList<>());
        while (list.size() < 4) list.add(null);

        list.set(index, loc);

        // Trim trailing nulls? Keep size=4 for clarity
        if (worldName == null) worldName = loc.getWorld().getName();
    }

    public Location getSpawnFor(BlastTeam team, int index) {
        List<Location> list = getSpawns(team);
        if (list.isEmpty()) return null;

        // prefer exact index if available and not null
        if (index >= 0 && index < list.size()) {
            Location at = list.get(index);
            if (at != null) return at;
        }

        // otherwise return first non-null
        for (Location l : list) {
            if (l != null) return l;
        }
        return null;
    }

    public boolean hasEnoughSpawns() {
        for (BlastTeam t : BlastTeam.values()) {
            List<Location> list = getSpawns(t);
            int count = 0;
            for (Location l : list) if (l != null) count++;
            if (count < 4) return false;
        }
        return true;
    }

    // -------------------------
    // Serialization
    // -------------------------

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;

        sec.set("world", worldName);
        sec.set("schematic", schematic);
        sec.set("paste", pasteLocation == null ? null : serializeLocation(pasteLocation));

        sec.set("region.min", regionMin == null ? null : serializeLocation(regionMin));
        sec.set("region.max", regionMax == null ? null : serializeLocation(regionMax));
        sec.set("ceiling.y", ceilingY);
        sec.set("start-spawn", startSpawn == null ? null : serializeLocation(startSpawn));

        // blocks list: "dx,dy,dz,MATERIAL"
        List<String> blocks = new ArrayList<>();
        if (savedBlocks != null) {
            for (BlastSavedBlock b : savedBlocks) {
                if (b != null) blocks.add(b.serialize());
            }
        }
        sec.set("blocks", blocks);

        for (BlastTeam t : BlastTeam.values()) {
            List<String> serialized = new ArrayList<>();
            for (Location loc : getSpawns(t)) {
                if (loc == null) {
                    serialized.add("");
                } else {
                    serialized.add(serializeLocation(loc));
                }
            }
            sec.set("spawns." + t.getKey(), serialized);
        }
    }

    public static BlastMap loadFromConfig(String name, ConfigurationSection sec) {
        BlastMap map = new BlastMap(name);
        if (sec == null) return map;

        map.setWorldName(sec.getString("world", null));
        map.setSchematic(sec.getString("schematic", null));

        Location paste = deserializeLocation(sec.getString("paste", null));
        if (paste != null) map.setPasteLocation(paste);

        Location rmin = deserializeLocation(sec.getString("region.min", null));
        Location rmax = deserializeLocation(sec.getString("region.max", null));
        map.setRegionMin(rmin);
        map.setRegionMax(rmax);

        if (sec.contains("ceiling.y")) {
            map.setCeilingY(sec.getInt("ceiling.y"));
        }

        Location startSpawn = deserializeLocation(sec.getString("start-spawn", null));
        if (startSpawn != null) map.setStartSpawn(startSpawn);

        // blocks
        List<String> blocks = sec.getStringList("blocks");
        List<BlastSavedBlock> parsed = new ArrayList<>();
        for (String s : blocks) {
            BlastSavedBlock b = BlastSavedBlock.deserialize(s);
            if (b != null) parsed.add(b);
        }
        map.setSavedBlocks(parsed);

        ConfigurationSection sp = sec.getConfigurationSection("spawns");
        if (sp != null) {
            for (String key : sp.getKeys(false)) {
                BlastTeam team = BlastTeam.fromKey(key);
                if (team == null) continue;

                List<String> list = sec.getStringList("spawns." + key);

                // Support either "setSpawn index" style or "addSpawn" style
                for (int i = 0; i < list.size() && i < 4; i++) {
                    Location loc = deserializeLocation(list.get(i));
                    if (loc != null) {
                        map.setSpawn(team, i, loc);
                    }
                }
            }
        }

        return map;
    }

    private static String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + ","
                + loc.getX() + ","
                + loc.getY() + ","
                + loc.getZ() + ","
                + loc.getYaw() + ","
                + loc.getPitch();
    }

    private static Location deserializeLocation(String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.split(",");
        if (p.length < 4) return null;

        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;

        try {
            double x = Double.parseDouble(p[1]);
            double y = Double.parseDouble(p[2]);
            double z = Double.parseDouble(p[3]);

            float yaw = 0f;
            float pitch = 0f;

            if (p.length >= 6) {
                yaw = Float.parseFloat(p[4]);
                pitch = Float.parseFloat(p[5]);
            }

            return new Location(w, x, y, z, yaw, pitch);
        } catch (Exception ignored) {
            return null;
        }
    }
}
