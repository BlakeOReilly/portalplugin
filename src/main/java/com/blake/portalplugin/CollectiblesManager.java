// src/main/java/com/blake/portalplugin/CollectiblesManager.java
package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CollectiblesManager {

    public static final int HOTBAR_SLOT = 8; // 0-based index -> 9th slot

    private final PortalPlugin plugin;
    private final NamespacedKey collectiblesKey;

    // Store previous item in slot 8 so we can restore when leaving HUB
    private final Map<UUID, ItemStack> previousSlotItem = new HashMap<>();

    public CollectiblesManager(PortalPlugin plugin) {
        this.plugin = plugin;
        this.collectiblesKey = new NamespacedKey(plugin, "collectibles_item");
    }

    // ---------------------------------------------------------
    // Collectibles hotbar item
    // ---------------------------------------------------------
    public void giveCollectiblesItem(Player player) {
        if (player == null) return;

        ItemStack current = player.getInventory().getItem(HOTBAR_SLOT);
        if (isCollectiblesItem(current)) {
            return; // already in place
        }

        UUID uuid = player.getUniqueId();
        if (!previousSlotItem.containsKey(uuid)) {
            previousSlotItem.put(uuid, current == null ? null : current.clone());
        }

        player.getInventory().setItem(HOTBAR_SLOT, createCollectiblesItem());
        player.updateInventory();
    }

    public void removeCollectiblesItem(Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        ItemStack stored = previousSlotItem.remove(uuid);

        ItemStack current = player.getInventory().getItem(HOTBAR_SLOT);

        if (stored != null) {
            player.getInventory().setItem(HOTBAR_SLOT, stored);
        } else if (isCollectiblesItem(current)) {
            player.getInventory().setItem(HOTBAR_SLOT, null);
        }

        player.updateInventory();
    }

    private ItemStack createCollectiblesItem() {
        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta meta = chest.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD.toString() + ChatColor.BOLD + "Collectibles");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(collectiblesKey, PersistentDataType.BYTE, (byte) 1);
            chest.setItemMeta(meta);
        }
        return chest;
    }

    public boolean isCollectiblesItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CHEST) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(collectiblesKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------
    // GUI Pages
    // ---------------------------------------------------------
    public enum Page {
        MAIN,
        PARTICLES,
        WIN_EFFECTS
    }

    public static class CollectiblesGUIHolder implements InventoryHolder {
        private final Page page;

        public CollectiblesGUIHolder(Page page) {
            this.page = page;
        }

        public Page getPage() {
            return page;
        }

        @Override
        public Inventory getInventory() {
            return null; // not used
        }
    }

    // ---------------------------------------------------------
    // Open GUIs
    // ---------------------------------------------------------
    public void openMainGUI(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CollectiblesGUIHolder(Page.MAIN),
                27,
                ChatColor.GOLD.toString() + ChatColor.BOLD + "Collectibles"
        );

        // Particles category
        ItemStack particles = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta pMeta = particles.getItemMeta();
        if (pMeta != null) {
            pMeta.setDisplayName(ChatColor.AQUA + "Particles");
            particles.setItemMeta(pMeta);
        }
        inv.setItem(11, particles);

        // Win Effects category
        ItemStack wins = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta wMeta = wins.getItemMeta();
        if (wMeta != null) {
            wMeta.setDisplayName(ChatColor.GREEN + "Win Effects");
            wins.setItemMeta(wMeta);
        }
        inv.setItem(15, wins);

        player.openInventory(inv);
    }

    public void openParticlesGUI(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CollectiblesGUIHolder(Page.PARTICLES),
                27,
                ChatColor.GOLD.toString() + ChatColor.BOLD + "Particles"
        );

        // Placeholder example item
        ItemStack example = new ItemStack(Material.REDSTONE);
        ItemMeta meta = example.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Example Particle");
            example.setItemMeta(meta);
        }
        inv.setItem(13, example);

        // Back button
        inv.setItem(22, createBackButton());

        player.openInventory(inv);
    }

    public void openWinEffectsGUI(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CollectiblesGUIHolder(Page.WIN_EFFECTS),
                27,
                ChatColor.GOLD.toString() + ChatColor.BOLD + "Win Effects"
        );

        // Placeholder example item
        ItemStack example = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = example.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Example Win Effect");
            example.setItemMeta(meta);
        }
        inv.setItem(13, example);

        // Back button
        inv.setItem(22, createBackButton());

        player.openInventory(inv);
    }

    private ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Back");
            back.setItemMeta(meta);
        }
        return back;
    }
}
