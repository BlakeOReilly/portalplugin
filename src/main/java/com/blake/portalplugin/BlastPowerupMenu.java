package com.blake.portalplugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;

public final class BlastPowerupMenu {

    private static final int UI_MAX_STACKS = 3;

    // Reserved menu slots (player inventory indices)
    // Base slots in main inventory rows: 9..35, indicator is base+1.
    private static final List<Integer> BASE_SLOTS = List.of(9, 11, 13, 15, 17, 19, 21, 23, 25, 27);

    private static final List<BlastPowerupType> UI_POWERUPS = List.of(
            BlastPowerupType.SPEED,
            BlastPowerupType.JUMP,
            BlastPowerupType.BLAST_SPEED,
            BlastPowerupType.BLASTER_DAMAGE,
            BlastPowerupType.DASH,
            BlastPowerupType.KNOCKBACK,
            BlastPowerupType.SLOW_SHOT,
            BlastPowerupType.BLIND_SHOT,
            BlastPowerupType.MARK_TARGET,
            BlastPowerupType.CONFUSION
    );

    private static final Set<Integer> RESERVED = buildReserved();

    private static final String PDC_KEY = "blast_powerup_menu_item"; // string marker
    private static final String PDC_BTN_PREFIX = "btn:";
    private static final String PDC_IND_PREFIX = "ind:";

    private BlastPowerupMenu() {}

    public static boolean isReservedSlot(int slot) {
        return RESERVED.contains(slot);
    }

    public static Set<Integer> getReservedSlots() {
        return RESERVED;
    }

    public static boolean isMenuItem(Plugin plugin, ItemStack it) {
        if (plugin == null || it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        NamespacedKey k = new NamespacedKey(plugin, PDC_KEY);
        String v = meta.getPersistentDataContainer().get(k, PersistentDataType.STRING);
        return v != null && !v.isBlank();
    }

    public static BlastPowerupType getClickedButtonType(Plugin plugin, ItemStack it) {
        if (plugin == null || it == null || !it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;

        String v = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, PDC_KEY), PersistentDataType.STRING);
        if (v == null) return null;
        if (!v.startsWith(PDC_BTN_PREFIX)) return null;

        return BlastPowerupType.fromKey(v.substring(PDC_BTN_PREFIX.length()));
    }

    public static void clear(Plugin plugin, Player p) {
        if (plugin == null || p == null) return;
        PlayerInventory inv = p.getInventory();
        if (inv == null) return;

        for (int slot : RESERVED) {
            ItemStack it = inv.getItem(slot);
            if (isMenuItem(plugin, it)) inv.setItem(slot, null);
        }

        try { p.updateInventory(); } catch (Throwable ignored) {}
    }

    public static void sync(Plugin plugin, Player p, BlastPowerupManager powerups) {
        if (plugin == null || p == null || !p.isOnline()) return;
        PlayerInventory inv = p.getInventory();
        if (inv == null) return;

        for (int i = 0; i < UI_POWERUPS.size(); i++) {
            BlastPowerupType type = UI_POWERUPS.get(i);
            int baseSlot = BASE_SLOTS.get(i);
            int indSlot = baseSlot + 1;

            setReserved(plugin, p, baseSlot, createButton(plugin, resolveButtonMaterial(type), type));

            int stacks = (powerups != null) ? powerups.getStacks(p, type) : 0;
            setReserved(plugin, p, indSlot, createIndicator(plugin, type, stacks));
        }

        try { p.updateInventory(); } catch (Throwable ignored) {}
    }

    private static void setReserved(Plugin plugin, Player p, int slot, ItemStack desired) {
        PlayerInventory inv = p.getInventory();

        ItemStack existing = inv.getItem(slot);
        if (existing != null && !existing.getType().isAir() && !isMenuItem(plugin, existing)) {
            // Move player item out safely
            inv.setItem(slot, null);
            var leftover = inv.addItem(existing);
            if (!leftover.isEmpty()) {
                for (ItemStack it : leftover.values()) {
                    if (it != null && !it.getType().isAir()) {
                        try { p.getWorld().dropItemNaturally(p.getLocation(), it); } catch (Throwable ignored) {}
                    }
                }
            }
        }

        inv.setItem(slot, desired);
    }

    private static ItemStack createButton(Plugin plugin, Material mat, BlastPowerupType type) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(type.getDisplay());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    PDC_BTN_PREFIX + type.getKey()
            );

            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack createIndicator(Plugin plugin, BlastPowerupType type, int stacks) {
        int clamped = Math.max(0, Math.min(UI_MAX_STACKS, stacks));
        if (clamped <= 0) return null;

        ItemStack it = new ItemStack(Material.BLUE_DYE, clamped);

        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Purchased: §b" + clamped + "/" + UI_MAX_STACKS);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    PDC_IND_PREFIX + type.getKey()
            );

            it.setItemMeta(meta);
        }
        return it;
    }

    private static Material resolveButtonMaterial(BlastPowerupType type) {
        return switch (type) {
            case SPEED -> Material.FEATHER;
            case JUMP -> Material.RABBIT_FOOT;
            case BLAST_SPEED -> Material.REDSTONE_TORCH;
            case BLASTER_DAMAGE -> Material.REDSTONE;
            case DASH -> Material.SUGAR;
            case KNOCKBACK -> Material.IRON_SWORD;
            case SLOW_SHOT -> Material.ICE;
            case BLIND_SHOT -> Material.INK_SAC;
            case MARK_TARGET -> Material.SPYGLASS;
            case CONFUSION -> Material.FERMENTED_SPIDER_EYE;
            default -> Material.NETHER_STAR;
        };
    }

    private static Set<Integer> buildReserved() {
        Set<Integer> slots = new java.util.HashSet<>();
        for (int base : BASE_SLOTS) {
            slots.add(base);
            slots.add(base + 1);
        }
        return Set.copyOf(slots);
    }
}
