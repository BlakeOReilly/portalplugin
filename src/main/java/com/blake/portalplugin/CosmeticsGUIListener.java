// src/main/java/com/blake/portalplugin/listeners/CosmeticsGUIListener.java
package com.blake.portalplugin.listeners;

import com.blake.portalplugin.CosmeticsManager;
import com.blake.portalplugin.CosmeticsManager.CosmeticsGUIHolder;
import com.blake.portalplugin.CosmeticsManager.Page;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class CosmeticsGUIListener implements Listener {

    private final JavaPlugin plugin;
    private final CosmeticsManager cosmeticsManager;

    public CosmeticsGUIListener(JavaPlugin plugin, CosmeticsManager cosmeticsManager) {
        this.plugin = plugin;
        this.cosmeticsManager = cosmeticsManager;
    }

    // ---------------------------------------------------------
    // Open Cosmetics GUI on right-click with the diamond
    // ---------------------------------------------------------
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        if (cosmeticsManager.isCosmeticsItem(hand)) {
            event.setCancelled(true);
            cosmeticsManager.openMainGUI(player);
        }
    }

    // ---------------------------------------------------------
    // Prevent moving/dropping/swapping Cosmetics diamond
    // and handle GUI clicks
    // ---------------------------------------------------------
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // BLOCK Q / CTRL+Q while hovering inventory
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            if (cosmeticsManager.isCosmeticsItem(current) || cosmeticsManager.isCosmeticsItem(cursor)) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin,
                        () -> cosmeticsManager.giveCosmeticsItem(player));
            }
            return;
        }

        // BLOCK NUMBER KEY SWAP (1–9) into/out of the cosmetics slot
        if (event.getClick() == ClickType.NUMBER_KEY) {
            if (event.getHotbarButton() == CosmeticsManager.HOTBAR_SLOT) {
                event.setCancelled(true);
                return;
            }
        }

        // BLOCK ANY MOVEMENT OF THE COSMETICS DIAMOND
        if (cosmeticsManager.isCosmeticsItem(current) || cosmeticsManager.isCosmeticsItem(cursor)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin,
                    () -> cosmeticsManager.giveCosmeticsItem(player));
            return;
        }

        // HANDLE GUI CLICKS (non-movable items)
        if (topInv != null && topInv.getHolder() instanceof CosmeticsGUIHolder holder) {
            event.setCancelled(true); // lock GUI contents

            if (clickedInv == null || clickedInv.getType() != InventoryType.CHEST)
                return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= topInv.getSize())
                return;

            ItemStack clicked = topInv.getItem(slot);
            if (clicked == null || clicked.getType() == Material.AIR)
                return;

            String strippedName = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()
                    ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                    : "";

            Page page = holder.getPage();

            // MAIN PAGE -> open categories
            if (page == Page.MAIN) {
                if (clicked.getType() == Material.LEATHER_HELMET && strippedName.equalsIgnoreCase("Hats")) {
                    cosmeticsManager.openHatsGUI(player);
                } else if (clicked.getType() == Material.IRON_SHOVEL && strippedName.equalsIgnoreCase("Tools")) {
                    cosmeticsManager.openToolsGUI(player);
                }
            }

            // SUBPAGES -> Back button
            if (page == Page.HATS || page == Page.TOOLS) {
                if (clicked.getType() == Material.ARROW && strippedName.equalsIgnoreCase("Back")) {
                    cosmeticsManager.openMainGUI(player);
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Prevent dropping the Cosmetics diamond from hotbar (Q / Ctrl+Q)
    // ---------------------------------------------------------
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (cosmeticsManager.isCosmeticsItem(dropped)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin,
                    () -> cosmeticsManager.giveCosmeticsItem(event.getPlayer()));
        }
    }

    // ---------------------------------------------------------
    // Prevent swapping the Cosmetics diamond to offhand
    // ---------------------------------------------------------
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (cosmeticsManager.isCosmeticsItem(event.getMainHandItem())
                || cosmeticsManager.isCosmeticsItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}
