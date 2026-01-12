package com.blake.portalplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

public final class BlastWoolUtil {

    private BlastWoolUtil() {}

    public static void breakWoolBurst(Location center, int radius) {
        if (center == null || center.getWorld() == null) return;
        int r = Math.max(1, radius);

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Location at = center.clone().add(x, y, z);
                    Block block = at.getBlock();
                    if (BlastItems.isColoredWool(block.getType())) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
