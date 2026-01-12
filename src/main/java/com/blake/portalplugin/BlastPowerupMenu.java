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

import java.util.Set;

public final class BlastPowerupMenu {

    // Reserved menu slots (player inventory indices)
    // Main inventory rows: 9..35
    public static final int SPEED_BTN = 9;
    public static final int SPEED_I1 = 10;
    public static final int SPEED_I2 = 11;
    public static final int SPEED_I3 = 12;

    public static final int DAMAGE_BTN = 14;
    public static final int DAMAGE_I1 = 15;
    public static final int DAMAGE_I2 = 16;
    public static final int DAMAGE_I3 = 17;

    public static final int JUMP_BTN = 18;
    public static final int JUMP_I1 = 19;
    public static final int JUMP_I2 = 20;
    public static final int JUMP_I3 = 21;

    public static final int BLASTSPD_BTN = 27;
    public static final int BLASTSPD_I1 = 28;
    public static final int BLASTSPD_I2 = 29;
    public static final int BLASTSPD_I3 = 30;

    private static final Set<Integer> RESERVED = Set.of(
            SPEED_BTN, SPEED_I1, SPEED_I2, SPEED_I3,
            DAMAGE_BTN, DAMAGE_I1, DAMAGE_I2, DAMAGE_I3,
            JUMP_BTN, JUMP_I1, JUMP_I2, JUMP_I3,
            BLASTSPD_BTN, BLASTSPD_I1, BLASTSPD_I2, BLASTSPD_I3
    );

    private static final String PDC_KEY = "blast_powerup_menu_item"; // string marker
    private static final String PDC_BTN_PREFIX = "btn:";
    private static final String PDC_IND_PREFIX = "ind:";

    private BlastPowerupMenu() {}

    public static boolean isReservedSlot(int slot) {
        return RESERVED.contains(slot);
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

        int sp = (powerups != null) ? powerups.getStacks(p, BlastPowerupType.SPEED) : 0;
        int jp = (powerups != null) ? powerups.getStacks(p, BlastPowerupType.JUMP) : 0;
        int bs = (powerups != null) ? powerups.getStacks(p, BlastPowerupType.BLAST_SPEED) : 0;
        int dmg = (powerups != null) ? powerups.getStacks(p, BlastPowerupType.BLASTER_DAMAGE) : 0;

        // Buttons
        setReserved(plugin, p, SPEED_BTN, createButton(plugin, Material.FEATHER, BlastPowerupType.SPEED));
        setReserved(plugin, p, JUMP_BTN, createButton(plugin, Material.RABBIT_FOOT, BlastPowerupType.JUMP));
        setReserved(plugin, p, BLASTSPD_BTN, createButton(plugin, Material.REDSTONE_TORCH, BlastPowerupType.BLAST_SPEED));
        setReserved(plugin, p, DAMAGE_BTN, createButton(plugin, Material.REDSTONE, BlastPowerupType.BLASTER_DAMAGE));

        // Indicators (blue -> purple enchanted)
        setReserved(plugin, p, SPEED_I1, createIndicator(plugin, BlastPowerupType.SPEED, 1, sp >= 1));
        setReserved(plugin, p, SPEED_I2, createIndicator(plugin, BlastPowerupType.SPEED, 2, sp >= 2));
        setReserved(plugin, p, SPEED_I3, createIndicator(plugin, BlastPowerupType.SPEED, 3, sp >= 3));

        setReserved(plugin, p, JUMP_I1, createIndicator(plugin, BlastPowerupType.JUMP, 1, jp >= 1));
        setReserved(plugin, p, JUMP_I2, createIndicator(plugin, BlastPowerupType.JUMP, 2, jp >= 2));
        setReserved(plugin, p, JUMP_I3, createIndicator(plugin, BlastPowerupType.JUMP, 3, jp >= 3));

        setReserved(plugin, p, BLASTSPD_I1, createIndicator(plugin, BlastPowerupType.BLAST_SPEED, 1, bs >= 1));
        setReserved(plugin, p, BLASTSPD_I2, createIndicator(plugin, BlastPowerupType.BLAST_SPEED, 2, bs >= 2));
        setReserved(plugin, p, BLASTSPD_I3, createIndicator(plugin, BlastPowerupType.BLAST_SPEED, 3, bs >= 3));

        setReserved(plugin, p, DAMAGE_I1, createIndicator(plugin, BlastPowerupType.BLASTER_DAMAGE, 1, dmg >= 1));
        setReserved(plugin, p, DAMAGE_I2, createIndicator(plugin, BlastPowerupType.BLASTER_DAMAGE, 2, dmg >= 2));
        setReserved(plugin, p, DAMAGE_I3, createIndicator(plugin, BlastPowerupType.BLASTER_DAMAGE, 3, dmg >= 3));

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

    private static ItemStack createIndicator(Plugin plugin, BlastPowerupType type, int index, boolean filled) {
        Material mat = filled ? Material.PURPLE_DYE : Material.BLUE_DYE;
        ItemStack it = new ItemStack(mat, 1);

        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(filled ? "§5§l" + index : "§9" + index);

            if (filled) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    PDC_IND_PREFIX + type.getKey() + ":" + index
            );

            it.setItemMeta(meta);
        }
        return it;
    }
}
