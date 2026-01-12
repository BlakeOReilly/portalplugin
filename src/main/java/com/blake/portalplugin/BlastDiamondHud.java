package com.blake.portalplugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class BlastDiamondHud {

    public static final int HUD_SLOT = 8; // 9th hotbar slot

    private static final String HUD_PDC_KEY = "blast_hud_item";
    private static final String HUD_TYPE_PANE = "pane";
    private static final String HUD_TYPE_DIAMOND = "diamond";

    private BlastDiamondHud() {}

    public static void sync(Plugin plugin, Player player) {
        if (plugin == null || player == null || !player.isOnline()) return;

        PlayerInventory inv = player.getInventory();
        if (inv == null) return;

        int diamonds = countRealDiamonds(plugin, player);

        ItemStack current = inv.getItem(HUD_SLOT);
        boolean currentIsHud = isHudItem(plugin, current);

        // If something non-HUD is sitting in slot 9, move it out safely first
        if (current != null && !current.getType().isAir() && !currentIsHud) {
            int empty = inv.firstEmpty();
            if (empty >= 0) {
                inv.setItem(empty, current);
            } else {
                // inventory full - drop it
                try {
                    player.getWorld().dropItemNaturally(player.getLocation(), current);
                } catch (Throwable ignored) {}
            }
            inv.setItem(HUD_SLOT, null);
        }

        // Set HUD item based on diamond count
        if (diamonds > 0) {
            ItemStack icon = createDiamondIcon(plugin, diamonds);
            inv.setItem(HUD_SLOT, icon);
        } else {
            ItemStack pane = createPlaceholderPane(plugin);
            inv.setItem(HUD_SLOT, pane);
        }

        player.updateInventory();
    }

    public static boolean isHudItem(Plugin plugin, ItemStack stack) {
        if (plugin == null || stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, HUD_PDC_KEY);
        String val = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return val != null && !val.isBlank();
    }

    public static boolean isHudSlot(int slot) {
        return slot == HUD_SLOT;
    }

    private static ItemStack createPlaceholderPane(Plugin plugin) {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8No Diamond");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, HUD_PDC_KEY),
                    PersistentDataType.STRING,
                    HUD_TYPE_PANE
            );

            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack createDiamondIcon(Plugin plugin, int count) {
        int amt = Math.max(1, Math.min(64, count));
        ItemStack it = new ItemStack(Material.DIAMOND, amt);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bDiamonds §7(" + count + ")");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, HUD_PDC_KEY),
                    PersistentDataType.STRING,
                    HUD_TYPE_DIAMOND
            );

            it.setItemMeta(meta);
        }
        return it;
    }

    private static int countRealDiamonds(Plugin plugin, Player player) {
        if (player == null) return 0;

        int total = 0;
        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null) return 0;

        for (ItemStack it : contents) {
            if (it == null || it.getType().isAir()) continue;
            if (it.getType() != Material.DIAMOND) continue;

            // Exclude our HUD diamond icon from counting
            if (isHudItem(plugin, it)) continue;

            total += it.getAmount();
        }
        return total;
    }
}
