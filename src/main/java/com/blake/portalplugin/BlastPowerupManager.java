package com.blake.portalplugin;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlastPowerupManager {

    public enum PurchaseResult {
        SUCCESS,
        NO_DIAMOND,
        MAXED
    }

    private static final int MAX_STACKS = 3;
    private static final int EFFECT_DURATION_TICKS = 20 * 60 * 60; // 1 hour

    private final Plugin plugin;

    // Optional (kept for compatibility with older constructor calls)
    @SuppressWarnings("unused")
    private final GameStateManager gameStateManager;

    // Per-player powerup stacks (0..3)
    private final Map<UUID, EnumMap<BlastPowerupType, Integer>> stacks = new ConcurrentHashMap<>();

    public BlastPowerupManager(Plugin plugin) {
        this(plugin, null);
    }

    // COMPAT: your PortalPlugin currently calls (this, gameStateManager)
    public BlastPowerupManager(Plugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
    }

    public int getStacks(Player p, BlastPowerupType type) {
        if (p == null || type == null) return 0;
        EnumMap<BlastPowerupType, Integer> m = stacks.get(p.getUniqueId());
        if (m == null) return 0;
        return Math.max(0, Math.min(MAX_STACKS, m.getOrDefault(type, 0)));
    }

    public void setStacks(Player p, BlastPowerupType type, int value) {
        if (p == null || type == null) return;
        int v = Math.max(0, Math.min(MAX_STACKS, value));
        stacks.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(BlastPowerupType.class)).put(type, v);
    }

    public void reset(Player p) {
        if (p == null) return;
        stacks.remove(p.getUniqueId());
        clearEffects(p);
    }

    public void clear(Player p) {
        // same as reset for now (clears stacks + effects)
        reset(p);
    }

    public PurchaseResult tryPurchase(Player p, BlastPowerupType type) {
        if (p == null || type == null) return PurchaseResult.NO_DIAMOND;

        int cur = getStacks(p, type);
        if (cur >= MAX_STACKS) return PurchaseResult.MAXED;

        if (!removeOneDiamond(p)) return PurchaseResult.NO_DIAMOND;

        setStacks(p, type, cur + 1);

        // Apply effects immediately (speed/jump). Other effects are read dynamically.
        applyEffects(p);

        return PurchaseResult.SUCCESS;
    }

    public void applyEffects(Player p) {
        if (p == null || !p.isOnline()) return;

        // SPEED: Speed 1 -> Speed 2 -> Speed 3
        int speedStacks = getStacks(p, BlastPowerupType.SPEED);
        // JUMP: Jump 2 -> Jump 3 -> Jump 4
        int jumpStacks = getStacks(p, BlastPowerupType.JUMP);

        // Clear then re-apply to avoid stale levels
        clearEffects(p);

        if (speedStacks > 0) {
            int amplifier = Math.max(0, speedStacks - 1); // 0..2
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, EFFECT_DURATION_TICKS, amplifier, false, false, true));
        }

        if (jumpStacks > 0) {
            int amplifier = Math.max(0, jumpStacks); // 1..3 (Jump II..IV)
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, EFFECT_DURATION_TICKS, amplifier, false, false, true));
        }
    }

    public void clearEffects(Player p) {
        if (p == null) return;
        try { p.removePotionEffect(PotionEffectType.SPEED); } catch (Throwable ignored) {}
        try { p.removePotionEffect(PotionEffectType.JUMP_BOOST); } catch (Throwable ignored) {}
    }

    /**
     * Blast speed: each stack reduces cooldown by 0.2s (200ms).
     */
    public long adjustBlasterCooldownMs(Player p, long baseMs) {
        int s = getStacks(p, BlastPowerupType.BLAST_SPEED);
        long adjusted = baseMs - (s * 200L);
        return Math.max(100L, adjusted);
    }

    /**
     * Basic blaster damage: base hit removes 1 armor piece.
     * +1 / +2 / +3 extra pieces for stacks 1/2/3 => total 2/3/4 pieces removed per hit.
     */
    public int getBasicArmorPiecesToRemove(Player shooter) {
        int s = getStacks(shooter, BlastPowerupType.BLASTER_DAMAGE);
        return 1 + Math.max(0, Math.min(3, s));
    }

    public int getDashDistanceBlocks(Player p) {
        int s = getStacks(p, BlastPowerupType.DASH);
        return switch (s) {
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 5;
            default -> 0;
        };
    }

    public boolean hasAtLeastOneDiamond(Player p) {
        if (p == null) return false;
        PlayerInventory inv = p.getInventory();
        if (inv == null) return false;

        ItemStack[] storage = inv.getStorageContents();
        if (storage != null) {
            for (ItemStack it : storage) {
                if (it != null && it.getType() == Material.DIAMOND && it.getAmount() > 0) return true;
            }
        }

        try {
            ItemStack off = inv.getItemInOffHand();
            return off != null && off.getType() == Material.DIAMOND && off.getAmount() > 0;
        } catch (Throwable ignored) {}

        return false;
    }

    private boolean removeOneDiamond(Player p) {
        PlayerInventory inv = p.getInventory();
        if (inv == null) return false;

        ItemStack[] storage = inv.getStorageContents();
        if (storage != null) {
            for (int i = 0; i < storage.length; i++) {
                ItemStack it = storage[i];
                if (it == null) continue;
                if (it.getType() != Material.DIAMOND) continue;

                int amt = it.getAmount();
                if (amt <= 0) continue;

                if (amt == 1) storage[i] = null;
                else it.setAmount(amt - 1);

                inv.setStorageContents(storage);
                try { p.updateInventory(); } catch (Throwable ignored) {}
                return true;
            }
        }

        // Offhand
        try {
            ItemStack off = inv.getItemInOffHand();
            if (off != null && off.getType() == Material.DIAMOND && off.getAmount() > 0) {
                if (off.getAmount() == 1) inv.setItemInOffHand(null);
                else off.setAmount(off.getAmount() - 1);

                try { p.updateInventory(); } catch (Throwable ignored) {}
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
