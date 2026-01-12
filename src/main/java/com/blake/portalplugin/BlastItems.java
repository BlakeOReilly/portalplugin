// src/main/java/com/blake/portalplugin/BlastItems.java
package com.blake.portalplugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class BlastItems {

    private static final String PDC_KEY = "blast_blaster_type";

    private static final String TYPE_BASIC = "basic";
    private static final String TYPE_BIG = "big";

    // NEW (middle generator + new blasters)
    private static final String TYPE_SCATTER = "scatter";
    private static final String TYPE_STRIKE = "strike";
    private static final String TYPE_RANGE = "range";

    private BlastItems() {}

    public static ItemStack createBasicBlaster(Plugin plugin) {
        ItemStack it = new ItemStack(Material.STONE_HOE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fBasic Blaster");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    TYPE_BASIC
            );
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack createBigBlaster(Plugin plugin) {
        ItemStack it = new ItemStack(Material.GOLDEN_HOE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Big Blaster");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    TYPE_BIG
            );
            it.setItemMeta(meta);
        }
        return it;
    }

    // ------------------------------------------------------------------
    // NEW: Special blasters (used by the diamond middle generator)
    // ------------------------------------------------------------------
    public static ItemStack createScatterBlaster(Plugin plugin) {
        ItemStack it = new ItemStack(Material.WOODEN_HOE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fScatter Blaster");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    TYPE_SCATTER
            );
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack createStrikeBlaster(Plugin plugin) {
        ItemStack it = new ItemStack(Material.DIAMOND_HOE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fStrike Blaster");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    TYPE_STRIKE
            );
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack createRangeBlaster(Plugin plugin) {
        ItemStack it = new ItemStack(Material.IRON_HOE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fRange Blaster");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    TYPE_RANGE
            );
            it.setItemMeta(meta);
        }
        return it;
    }

    public static boolean isBasicBlaster(Plugin plugin, ItemStack it) {
        if (it == null || it.getType() != Material.STONE_HOE) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;

        String tagged = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_KEY),
                PersistentDataType.STRING
        );
        if (TYPE_BASIC.equalsIgnoreCase(tagged)) return true;

        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        return stripColor(name).equalsIgnoreCase("Basic Blaster");
    }

    public static boolean isBigBlaster(Plugin plugin, ItemStack it) {
        if (it == null || it.getType() != Material.GOLDEN_HOE) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;

        String tagged = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_KEY),
                PersistentDataType.STRING
        );
        if (TYPE_BIG.equalsIgnoreCase(tagged)) return true;

        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        return stripColor(name).equalsIgnoreCase("Big Blaster");
    }

    public static boolean isScatterBlaster(Plugin plugin, ItemStack it) {
        if (it == null || it.getType() != Material.WOODEN_HOE) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;

        String tagged = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_KEY),
                PersistentDataType.STRING
        );
        if (TYPE_SCATTER.equalsIgnoreCase(tagged)) return true;

        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        return stripColor(name).equalsIgnoreCase("Scatter Blaster");
    }

    public static boolean isStrikeBlaster(Plugin plugin, ItemStack it) {
        if (it == null || it.getType() != Material.DIAMOND_HOE) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;

        String tagged = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_KEY),
                PersistentDataType.STRING
        );
        if (TYPE_STRIKE.equalsIgnoreCase(tagged)) return true;

        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        return stripColor(name).equalsIgnoreCase("Strike Blaster");
    }

    public static boolean isRangeBlaster(Plugin plugin, ItemStack it) {
        if (it == null || it.getType() != Material.IRON_HOE) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;

        String tagged = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_KEY),
                PersistentDataType.STRING
        );
        if (TYPE_RANGE.equalsIgnoreCase(tagged)) return true;

        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        return stripColor(name).equalsIgnoreCase("Range Blaster");
    }

    public static boolean isColoredWool(Material mat) {
        if (mat == null) return false;
        return mat.name().endsWith("_WOOL");
    }

    public static Material getWoolForTeam(BlastTeam team) {
        if (team == null) return Material.WHITE_WOOL;
        return switch (team) {
            case RED -> Material.RED_WOOL;
            case GREEN -> Material.GREEN_WOOL;
            case YELLOW -> Material.YELLOW_WOOL;
            case BLUE -> Material.BLUE_WOOL;
        };
    }

    public static Material getTeamWool(BlastTeam team) {
        return getWoolForTeam(team);
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}
