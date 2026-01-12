package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BlastCooldownHudService {

    private static final long UPDATE_TICKS = 2L;

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final BlastPowerupManager powerupManager;
    private final BlastCooldownTracker cooldownTracker;

    private final Map<UUID, BossBar> dashBars = new HashMap<>();
    private final Set<UUID> trackedExp = new HashSet<>();

    private BukkitRunnable task;

    public BlastCooldownHudService(
            PortalPlugin plugin,
            GameStateManager gameStateManager,
            BlastPowerupManager powerupManager,
            BlastCooldownTracker cooldownTracker
    ) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
        this.powerupManager = powerupManager;
        this.cooldownTracker = cooldownTracker;
        start();
    }

    public void shutdown() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
        for (BossBar bar : dashBars.values()) {
            if (bar != null) {
                bar.removeAll();
            }
        }
        dashBars.clear();
        trackedExp.clear();
    }

    private void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p == null || !p.isOnline()) continue;

                    if (gameStateManager.getGameState(p) != GameState.BLAST) {
                        resetPlayerHud(p);
                        continue;
                    }

                    trackedExp.add(p.getUniqueId());
                    updateBlasterCooldownBar(p);
                    updateDashCooldownBar(p);
                }
            }
        };
        task.runTaskTimer(plugin, UPDATE_TICKS, UPDATE_TICKS);
    }

    private void resetPlayerHud(Player p) {
        UUID id = p.getUniqueId();
        if (trackedExp.remove(id)) {
            try {
                p.setExp(0.0f);
                p.setLevel(0);
            } catch (Throwable ignored) {}
        }

        BossBar bar = dashBars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void updateBlasterCooldownBar(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        BlastCooldownTracker.CooldownType type = getHeldBlasterType(p, held);

        if (type == null) {
            try {
                p.setExp(0.0f);
                p.setLevel(0);
            } catch (Throwable ignored) {}
            return;
        }

        double progress = cooldownTracker.getProgress(p.getUniqueId(), type);
        try {
            p.setExp((float) progress);
            p.setLevel(0);
        } catch (Throwable ignored) {}
    }

    private void updateDashCooldownBar(Player p) {
        if (powerupManager == null || powerupManager.getDashDistanceBlocks(p) <= 0) {
            BossBar existing = dashBars.remove(p.getUniqueId());
            if (existing != null) existing.removeAll();
            return;
        }

        BossBar bar = dashBars.computeIfAbsent(
                p.getUniqueId(),
                id -> Bukkit.createBossBar("Dash Cooldown", BarColor.BLUE, BarStyle.SOLID)
        );

        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }
        bar.setVisible(true);

        UUID id = p.getUniqueId();
        double progress = cooldownTracker.getProgress(id, BlastCooldownTracker.CooldownType.DASH);
        long remaining = cooldownTracker.getRemainingMs(id, BlastCooldownTracker.CooldownType.DASH);

        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        if (remaining <= 0) {
            bar.setTitle("§bDash Ready");
        } else {
            double seconds = remaining / 1000.0;
            bar.setTitle(String.format("§bDash Cooldown: §f%.1fs", seconds));
        }
    }

    private BlastCooldownTracker.CooldownType getHeldBlasterType(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (BlastItems.isBasicBlaster(plugin, item)) return BlastCooldownTracker.CooldownType.BASIC;
        if (BlastItems.isBigBlaster(plugin, item)) return BlastCooldownTracker.CooldownType.BIG;
        if (BlastItems.isScatterBlaster(plugin, item)) return BlastCooldownTracker.CooldownType.SCATTER;
        if (BlastItems.isRangeBlaster(plugin, item)) return BlastCooldownTracker.CooldownType.RANGE;
        if (BlastItems.isStrikeBlaster(plugin, item)) return BlastCooldownTracker.CooldownType.STRIKE;
        return null;
    }
}
