package com.blake.portalplugin;

import java.util.HashMap;
import java.util.UUID;

public class PlayerStatsManager {

    private static final HashMap<UUID, Integer> coins = new HashMap<>();

    public static int getCoins(UUID uuid) {
        return coins.getOrDefault(uuid, 0);
    }

    public static void setCoins(UUID uuid, int amount) {
        coins.put(uuid, amount);
    }

    public static void addCoins(UUID uuid, int amount) {
        coins.put(uuid, getCoins(uuid) + amount);
    }

    public static void subtractCoins(UUID uuid, int amount) {
        coins.put(uuid, Math.max(0, getCoins(uuid) - amount));
    }

    // Expand later to include minigame wins, kills, etc.
}
