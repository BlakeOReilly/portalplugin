package com.blake.portalplugin.worldedit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BlockEditTask extends BukkitRunnable {

    private final Plugin plugin;
    private final UUID playerId;

    private final World world;
    private final int minX, minY, minZ;
    private final int dx, dy, dz;
    private final long total;

    private final int blocksPerTick;

    private final BlockEditOperation operation;
    private final Material setTo;              // for FILL
    private final Material replaceOnly;        // optional filter; if non-null, only replace this

    private long index = 0;
    private long changed = 0;

    public BlockEditTask(
            Plugin plugin,
            UUID playerId,
            World world,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int blocksPerTick,
            BlockEditOperation operation,
            Material setTo,
            Material replaceOnly
    ) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.world = world;

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;

        this.dx = (maxX - minX + 1);
        this.dy = (maxY - minY + 1);
        this.dz = (maxZ - minZ + 1);

        this.total = (long) dx * (long) dy * (long) dz;

        this.blocksPerTick = Math.max(1, blocksPerTick);

        this.operation = operation;
        this.setTo = setTo;
        this.replaceOnly = replaceOnly;
    }

    @Override
    public void run() {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline()) {
            cancel();
            return;
        }

        int processedThisTick = 0;

        while (processedThisTick < blocksPerTick && index < total) {
            int x = minX + (int) (index % dx);
            int y = minY + (int) ((index / dx) % dy);
            int z = minZ + (int) (index / ((long) dx * (long) dy));

            Block b = world.getBlockAt(x, y, z);

            if (replaceOnly == null || b.getType() == replaceOnly) {
                if (operation == BlockEditOperation.CLEAR) {
                    if (b.getType() != Material.AIR) {
                        b.setType(Material.AIR, false);
                        changed++;
                    }
                } else if (operation == BlockEditOperation.FILL) {
                    if (setTo != null && b.getType() != setTo) {
                        b.setType(setTo, false);
                        changed++;
                    }
                }
            }

            index++;
            processedThisTick++;
        }

        // progress every ~5%
        if (total > 0) {
            long pct = (index * 100L) / total;
            if (pct % 5 == 0 && processedThisTick > 0) {
                p.sendActionBar("Editing: " + pct + "% (" + index + "/" + total + ")");
            }
        }

        if (index >= total) {
            p.sendMessage("Done. Changed " + changed + " blocks.");
            cancel();
        }
    }

    public static Bounds fromTwoLocations(Location a, Location b) {
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
}