package com.blake.portalplugin.worldedit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BlockPasteTask extends BukkitRunnable {

    private final Plugin plugin;
    private final UUID playerId;

    private final World world;
    private final int originX, originY, originZ;

    private final Clipboard clipboard;
    private final boolean pasteAir;

    private final int blocksPerTick;
    private final long total;

    private long index = 0;
    private long placed = 0;

    public BlockPasteTask(
            Plugin plugin,
            UUID playerId,
            World world,
            int originX, int originY, int originZ,
            Clipboard clipboard,
            int blocksPerTick,
            boolean pasteAir
    ) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.clipboard = clipboard;
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.pasteAir = pasteAir;

        this.total = clipboard != null ? clipboard.getVolume() : 0;
    }

    @Override
    public void run() {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline()) {
            cancel();
            return;
        }
        if (clipboard == null || total <= 0) {
            p.sendMessage("Clipboard is empty.");
            cancel();
            return;
        }

        int w = clipboard.getWidth();
        int h = clipboard.getHeight();
        int l = clipboard.getLength();

        int processedThisTick = 0;

        while (processedThisTick < blocksPerTick && index < total) {
            int x = (int) (index % w);
            int y = (int) ((index / w) % h);
            int z = (int) (index / ((long) w * (long) h));

            String dataString = clipboard.getRawData()[(int) index];

            // skip air if requested
            if (!pasteAir && isAirDataString(dataString)) {
                index++;
                processedThisTick++;
                continue;
            }

            int wx = originX + x;
            int wy = originY + y;
            int wz = originZ + z;

            Block b = world.getBlockAt(wx, wy, wz);

            try {
                BlockData bd = Bukkit.createBlockData(dataString);
                b.setBlockData(bd, false);
            } catch (Throwable ignored) {
                // fallback: try just set type
                Material m = materialFromDataString(dataString);
                if (m == null) m = Material.AIR;
                b.setType(m, false);
            }

            placed++;
            index++;
            processedThisTick++;
        }

        // progress every ~5%
        if (total > 0) {
            long pct = (index * 100L) / total;
            if (pct % 5 == 0 && processedThisTick > 0) {
                p.sendActionBar("Pasting: " + pct + "% (" + index + "/" + total + ")");
            }
        }

        if (index >= total) {
            p.sendMessage("Paste complete. Placed " + placed + " blocks.");
            cancel();
        }
    }

    private boolean isAirDataString(String s) {
        if (s == null) return true;
        // getAsString() usually returns "minecraft:air"
        String lower = s.toLowerCase();
        return lower.equals("minecraft:air") || lower.equals("air") || lower.startsWith("minecraft:air[");
    }

    private Material materialFromDataString(String s) {
        if (s == null || s.isBlank()) return null;

        String base = s;
        int br = base.indexOf('[');
        if (br >= 0) base = base.substring(0, br);

        if (base.contains(":")) {
            base = base.substring(base.indexOf(':') + 1);
        }

        Material m = Material.matchMaterial(base);
        if (m == null || !m.isBlock()) return null;
        return m;
    }
}
