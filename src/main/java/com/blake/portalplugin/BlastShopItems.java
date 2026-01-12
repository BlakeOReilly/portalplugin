package com.blake.portalplugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class BlastShopItems {

    private BlastShopItems() {}

    public static NamespacedKey npcKey(PortalPlugin plugin) {
        return new NamespacedKey(plugin, "blast_shop_npc_team");
    }

    public static NamespacedKey shopIdKey(PortalPlugin plugin) {
        return new NamespacedKey(plugin, "blast_shop_id");
    }

    public static NamespacedKey shopCostKey(PortalPlugin plugin) {
        return new NamespacedKey(plugin, "blast_shop_cost");
    }

    public static ItemStack createDiamondPlaceholder(PortalPlugin plugin) {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Diamonds");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack makeShopItem(PortalPlugin plugin, Material mat, int amount, String name, String id, int cost, List<String> extraLore) {
        ItemStack it = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);

            List<String> lore = new ArrayList<>();
            lore.add("§7Cost: §b" + cost + " §7Elim Token(s)");
            if (extraLore != null) lore.addAll(extraLore);

            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(shopIdKey(plugin), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(shopCostKey(plugin), PersistentDataType.INTEGER, cost);

            it.setItemMeta(meta);
        }
        return it;
    }

    public static String getShopId(PortalPlugin plugin, ItemStack it) {
        if (plugin == null || it == null) return null;
        if (!it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;

        return meta.getPersistentDataContainer().get(shopIdKey(plugin), PersistentDataType.STRING);
    }

    public static int getShopCost(PortalPlugin plugin, ItemStack it) {
        if (plugin == null || it == null) return 0;
        if (!it.hasItemMeta()) return 0;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return 0;

        Integer v = meta.getPersistentDataContainer().get(shopCostKey(plugin), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }
}
