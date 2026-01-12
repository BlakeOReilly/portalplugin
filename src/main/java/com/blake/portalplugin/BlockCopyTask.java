package com.blake.portalplugin.worldedit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BlockCopyTask extends BukkitRunnable {

    private final Plugin plugin;
    private final UUID playerId;
    private final World world;

    private final int minX, minY, minZ;
    private final int dx, dy, dz;
    private final long total;

    private final int blocksPerTick;
    private final ClipboardManager clipboardManager;

    private final String[] data; // same indexing as below

    private long index = 0;

    public BlockCopyTask(
            Plugin plugin,
            UUID playerId,
            World world,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int blocksPerTick,
            ClipboardManager clipboardManager
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
        this.clipboardManager = clipboardManager;

        this.data = new String[(int) total];
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
            data[(int) index] = b.getBlockData().getAsString();

            index++;
            processedThisTick++;
        }

        // progress every ~5%
        if (total > 0) {
            long pct = (index * 100L) / total;
            if (pct % 5 == 0 && processedThisTick > 0) {
                p.sendActionBar("Copying: " + pct + "% (" + index + "/" + total + ")");
            }
        }

        if (index >= total) {
            Clipboard cb = new Clipboard(dx, dy, dz, data);
            clipboardManager.setClipboard(playerId, cb);

            p.sendMessage("Copied " + total + " blocks to clipboard. Size: "
                    + dx + "x" + dy + "x" + dz + ".");
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
