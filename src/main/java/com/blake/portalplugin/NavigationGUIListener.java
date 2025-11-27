// src/main/java/com/blake/portalplugin/listeners/NavigationGUIListener.java
package com.blake.portalplugin.listeners;

import com.blake.portalplugin.NavigationManager;
import com.blake.portalplugin.NavigationManager.NavigationGUIHolder;
import com.blake.portalplugin.NavigationManager.Page;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class NavigationGUIListener implements Listener {

    private final Plugin plugin;
    private final NavigationManager navigationManager;

    public NavigationGUIListener(Plugin plugin, NavigationManager navigationManager) {
        this.plugin = plugin;
        this.navigationManager = navigationManager;
    }

    // ---------------------------------------------------------
    // Open Navigation GUI on right-click with the compass
    // ---------------------------------------------------------
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        if (navigationManager.isNavigationItem(hand)) {
            event.setCancelled(true);
            navigationManager.openMainGUI(player);
        }
    }

    // ---------------------------------------------------------
    // Prevent moving Navigation compass and handle GUI clicks
    // ---------------------------------------------------------
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // 1) Always protect the Navigation compass (any inventory)
        if (navigationManager.isNavigationItem(current) || navigationManager.isNavigationItem(cursor)) {
            event.setCancelled(true);

            // Fix Ctrl+Q / Drop / swaps by scheduling a restore
            Bukkit.getScheduler().runTask(plugin, () -> {
                navigationManager.giveNavigationItem(player);
                player.updateInventory();
            });
            return;
        }

        // 2) If the top inventory is one of our Navigation GUIs, cancel all clicks in it
        if (topInv != null && topInv.getHolder() instanceof NavigationGUIHolder holder) {
            event.setCancelled(true);

            // Ignore clicks outside the top chest
            if (clickedInv == null || clickedInv.getType() != InventoryType.CHEST) {
                return;
            }

            int rawSlot = event.getRawSlot();
            if (rawSlot < 0 || rawSlot >= topInv.getSize()) {
                return;
            }

            ItemStack clicked = topInv.getItem(rawSlot);
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }

            Page page = holder.getPage();

            if (page == Page.MAIN) {
                String name = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()
                        ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                        : "";

                // Close button
                if (clicked.getType() == Material.BARRIER && name.equalsIgnoreCase("Close")) {
                    player.closeInventory();
                    return;
                }

                // Server item: compass with server name
                if (clicked.getType() == Material.COMPASS && !name.isEmpty()) {
                    navigationManager.sendToServer(player, name);
                }
            }
        }

        // 3) Block hotbar swaps that might move the Navigation compass
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton == NavigationManager.HOTBAR_SLOT) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    navigationManager.giveNavigationItem(player);
                    player.updateInventory();
                });
            }
        }

        // 4) Extra protection against drop-clicks from GUI (Ctrl+Q etc)
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            if (navigationManager.isNavigationItem(current)) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    navigationManager.giveNavigationItem(player);
                    player.updateInventory();
                });
            }
        }
    }

    // ---------------------------------------------------------
    // Prevent dragging the compass around in inventories
    // ---------------------------------------------------------
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getOldCursor();
        if (navigationManager.isNavigationItem(cursor)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                navigationManager.giveNavigationItem(player);
                player.updateInventory();
            });
        }
    }

    // ---------------------------------------------------------
    // Prevent dropping the Navigation compass
    // ---------------------------------------------------------
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (navigationManager.isNavigationItem(stack)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                navigationManager.giveNavigationItem(event.getPlayer());
                event.getPlayer().updateInventory();
            });
        }
    }

    // ---------------------------------------------------------
    // Prevent swapping the Navigation compass to offhand
    // ---------------------------------------------------------
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (navigationManager.isNavigationItem(event.getMainHandItem())
                || navigationManager.isNavigationItem(event.getOffHandItem())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                navigationManager.giveNavigationItem(event.getPlayer());
                event.getPlayer().updateInventory();
            });
        }
    }
}
