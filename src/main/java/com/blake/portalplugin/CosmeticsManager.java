// src/main/java/com/blake/portalplugin/CosmeticsManager.java
package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CosmeticsManager {

    public static final int HOTBAR_SLOT = 4;

    private final PortalPlugin plugin;
    private final NamespacedKey cosmeticsKey;

    private final Map<UUID, ItemStack> previousSlotItem = new HashMap<>();

    public CosmeticsManager(PortalPlugin plugin) {
        this.plugin = plugin;
        this.cosmeticsKey = new NamespacedKey(plugin, "cosmetics_item");
    }

    public void giveCosmeticsItem(Player player) {
        if (player == null) return;

        ItemStack current = player.getInventory().getItem(HOTBAR_SLOT);
        if (isCosmeticsItem(current)) return;

        GameState state = plugin.getGameStateManager().getGameState(player);
        if (state == GameState.SPLEEF) return; // Never allow in spleef

        UUID uuid = player.getUniqueId();
        if (!previousSlotItem.containsKey(uuid))
            previousSlotItem.put(uuid, current == null ? null : current.clone());

        player.getInventory().setItem(HOTBAR_SLOT, createCosmeticsItem());
        player.updateInventory();
    }

    public void removeCosmeticsItem(Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        ItemStack old = previousSlotItem.remove(uuid);

        ItemStack current = player.getInventory().getItem(HOTBAR_SLOT);

        if (old != null) {
            player.getInventory().setItem(HOTBAR_SLOT, old);
        } else if (isCosmeticsItem(current)) {
            player.getInventory().setItem(HOTBAR_SLOT, null);
        }

        player.updateInventory();
    }

    private ItemStack createCosmeticsItem() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Game Cosmetics");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(cosmeticsKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isCosmeticsItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.DIAMOND) return false;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;

        return meta.getPersistentDataContainer().has(cosmeticsKey, PersistentDataType.BYTE);
    }

    // ---------------- GUI Logic ----------------

    public enum Page {
        MAIN, HATS, TOOLS
    }

    public static class CosmeticsGUIHolder implements InventoryHolder {
        private final Page page;

        public CosmeticsGUIHolder(Page page) {
            this.page = page;
        }

        public Page getPage() {
            return page;
        }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void openMainGUI(Player player) {
        String activeGame = plugin.getActiveGame() == null ? "none" : plugin.getActiveGame();

        Inventory inv = Bukkit.createInventory(
                new CosmeticsGUIHolder(Page.MAIN),
                27,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Game Cosmetics " + ChatColor.GRAY + "(" + activeGame + ")"
        );

        ItemStack hats = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta h = hats.getItemMeta();
        if (h != null) {
            h.setDisplayName(ChatColor.GOLD + "Hats");
            hats.setItemMeta(h);
        }
        inv.setItem(11, hats);

        ItemStack tools = new ItemStack(Material.IRON_SHOVEL);
        ItemMeta t = tools.getItemMeta();
        if (t != null) {
            t.setDisplayName(ChatColor.GREEN + "Tools");
            tools.setItemMeta(t);
        }
        inv.setItem(15, tools);

        player.openInventory(inv);
    }

    public void openHatsGUI(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CosmeticsGUIHolder(Page.HATS),
                27,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Cosmetics - Hats"
        );

        ItemStack hat = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta meta = hat.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Example Hat");
            hat.setItemMeta(meta);
        }
        inv.setItem(13, hat);

        inv.setItem(22, createBackButton());
        player.openInventory(inv);
    }

    public void openToolsGUI(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CosmeticsGUIHolder(Page.TOOLS),
                27,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Cosmetics - Tools"
        );

        ItemStack tool = new ItemStack(Material.DIAMOND_SHOVEL);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Example Tool");
            tool.setItemMeta(meta);
        }
        inv.setItem(13, tool);

        inv.setItem(22, createBackButton());
        player.openInventory(inv);
    }

    private ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.RED + "Back");
            back.setItemMeta(m);
        }
        return back;
    }
}
