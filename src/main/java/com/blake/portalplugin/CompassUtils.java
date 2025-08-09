package com.blake.portalplugin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CompassUtils {

    /**
     * Creates the locked server selector compass.
     */
    public static ItemStack createCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aServer Selector");
            compass.setItemMeta(meta);
        }
        return compass;
    }
}