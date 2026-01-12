package com.blake.portalplugin;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class BlastLandingUtil {

    private BlastLandingUtil() {}

    public static Location findSafeLanding(Location impact, Player player) {
        if (impact == null || impact.getWorld() == null) return player.getLocation();

        Location base = impact.getBlock().getLocation().add(0.5, 0, 0.5);

        for (int i = 0; i <= 3; i++) {
            Location check = base.clone().add(0, i, 0);
            Block feet = check.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            Block below = feet.getRelative(0, -1, 0);

            if (feet.isPassable() && head.isPassable() && !below.isPassable()) {
                return check.clone().add(0, 0.1, 0);
            }
        }

        return base.clone().add(0, 1.0, 0);
    }
}
