package com.blake.portalplugin;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

public final class BlastStrikeUtil {

    public static final String META_STRIKE_CHARGING = "blast_strike_charging";
    public static final String META_STRIKE_CANCEL_REQUESTED = "blast_strike_cancel_requested";

    private BlastStrikeUtil() {}

    public static void markCharging(Plugin plugin, Player p) {
        if (plugin == null || p == null) return;
        p.setMetadata(META_STRIKE_CHARGING, new FixedMetadataValue(plugin, true));
        // clear any stale cancel request
        p.removeMetadata(META_STRIKE_CANCEL_REQUESTED, plugin);
    }

    public static void clearCharging(Plugin plugin, Player p) {
        if (plugin == null || p == null) return;
        p.removeMetadata(META_STRIKE_CHARGING, plugin);
        p.removeMetadata(META_STRIKE_CANCEL_REQUESTED, plugin);
    }

    public static boolean isCharging(Player p) {
        if (p == null) return false;
        return p.hasMetadata(META_STRIKE_CHARGING);
    }

    public static void requestCancel(Plugin plugin, Player p) {
        if (plugin == null || p == null) return;
        if (!isCharging(p)) return;
        p.setMetadata(META_STRIKE_CANCEL_REQUESTED, new FixedMetadataValue(plugin, true));
    }

    public static boolean consumeCancelRequest(Plugin plugin, Player p) {
        if (plugin == null || p == null) return false;
        if (!p.hasMetadata(META_STRIKE_CANCEL_REQUESTED)) return false;
        p.removeMetadata(META_STRIKE_CANCEL_REQUESTED, plugin);
        return true;
    }
}
