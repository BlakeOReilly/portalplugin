package com.blake.portalplugin;

import org.bukkit.entity.Player;
import org.bukkit.Location;

public class HubStatsPlugin {
    public String getPlayerRank(Player player) {
        // Implementation here
        return "Rank";
    }

    public int getPlayerCoins(Player player) {
        // Implementation here
        return 0;
    }

    public void giveHubCompass(Player player) {
        // Implementation here
    }

    public Location getHubLocation() {
        // Implementation here
        return new Location(null, 0, 0, 0);
    }
}