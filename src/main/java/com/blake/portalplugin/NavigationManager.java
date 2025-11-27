// src/main/java/com/blake/portalplugin/NavigationManager.java
package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NavigationManager {

    // Hotbar slot index (0-based). 0 = first slot ("slot 1" to players)
    public static final int HOTBAR_SLOT = 0;

    private final PortalPlugin plugin;
    private final List<String> serverNames = new ArrayList<>();

    public enum Page {
        MAIN
    }

    /**
     * Custom InventoryHolder so we can detect our GUI in listeners.
     */
    public static class NavigationGUIHolder implements InventoryHolder {
        private final Page page;

        public NavigationGUIHolder(Page page) {
            this.page = page;
        }

        public Page getPage() {
            return page;
        }

        @Override
        public Inventory getInventory() {
            return null; // Not used; Bukkit will supply real inventory instance
        }
    }

    public NavigationManager(PortalPlugin plugin) {
        this.plugin = plugin;

        // Load server names from config: navigation-servers: [ "Pvp-1", "Spleef-1", ... ]
        List<String> cfgList = plugin.getConfig().getStringList("navigation-servers");
        if (cfgList != null && !cfgList.isEmpty()) {
            serverNames.addAll(cfgList);
        }

        // Fallback if none configured
        if (serverNames.isEmpty()) {
            serverNames.add("Pvp-1");
        }
    }

    public Plugin getPlugin() {
        return plugin;
    }

    // -------------------------------------------------------------------
    // Hotbar item (compass) management
    // -------------------------------------------------------------------
    public boolean isNavigationItem(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getType() != Material.COMPASS) return false;
        if (!stack.hasItemMeta()) return false;

        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasDisplayName()) return false;

        String name = ChatColor.stripColor(meta.getDisplayName());
        return "Navigate".equalsIgnoreCase(name);
    }

    public void giveNavigationItem(Player player) {
        if (player == null) return;

        ItemStack current = player.getInventory().getItem(HOTBAR_SLOT);
        if (isNavigationItem(current)) {
            // Already correct item in correct slot
            return;
        }

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD.toString() + ChatColor.BOLD + "Navigate");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Right-click to open server navigator");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        compass.setItemMeta(meta);

        compass.setAmount(1);

        player.getInventory().setItem(HOTBAR_SLOT, compass);
        player.updateInventory();
    }

    public void removeNavigationItem(Player player) {
        if (player == null) return;

        ItemStack current = player.getInventory().getItem(HOTBAR_SLOT);
        if (isNavigationItem(current)) {
            player.getInventory().setItem(HOTBAR_SLOT, null);
            player.updateInventory();
        }
    }

    // -------------------------------------------------------------------
    // GUI
    // -------------------------------------------------------------------
    public void openMainGUI(Player player) {
        if (player == null) return;

        int size = 27; // 3 rows
        Inventory inv = Bukkit.createInventory(
                new NavigationGUIHolder(Page.MAIN),
                size,
                ChatColor.GOLD.toString() + ChatColor.BOLD + "Navigate"
        );

        // Lay out servers in a simple grid
        int index = 10; // start near center (row 2, col 2)
        for (String server : serverNames) {
            if (index >= size - 1) break; // leave at least one slot for "Close" if desired

            ItemStack item = new ItemStack(Material.COMPASS);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + server);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to connect to " + ChatColor.AQUA + server);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);

            inv.setItem(index, item);
            index++;
        }

        // Optional close item in last slot
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED.toString() + ChatColor.BOLD + "Close");
        close.setItemMeta(closeMeta);
        inv.setItem(size - 1, close);

        player.openInventory(inv);
    }

    // -------------------------------------------------------------------
    // Velocity / BungeeCord-compatible plugin message: connect player
    // -------------------------------------------------------------------
    public void sendToServer(Player player, String serverName) {
        if (player == null || serverName == null || serverName.isEmpty()) return;

        try {
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(msgBytes);

            // BungeeCord-compatible "Connect" subchannel
            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", msgBytes.toByteArray());

        } catch (IOException e) {
            plugin.getLogger().warning("[PortalPlugin] Failed to send player to server '" +
                    serverName + "': " + e.getMessage());
        }
    }
}
