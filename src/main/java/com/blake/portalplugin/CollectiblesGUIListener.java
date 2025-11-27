package com.blake.portalplugin.listeners;

import com.blake.portalplugin.CollectiblesManager;
import com.blake.portalplugin.CollectiblesManager.CollectiblesGUIHolder;
import com.blake.portalplugin.CollectiblesManager.Page;
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

public class CollectiblesGUIListener implements Listener {

    private final JavaPlugin plugin;
    private final CollectiblesManager collectiblesManager;

    public CollectiblesGUIListener(JavaPlugin plugin, CollectiblesManager collectiblesManager) {
        this.plugin = plugin;
        this.collectiblesManager = collectiblesManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        if (collectiblesManager.isCollectiblesItem(hand)) {
            event.setCancelled(true);
            collectiblesManager.openMainGUI(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // BLOCK CTRL+Q & Q
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            if (collectiblesManager.isCollectiblesItem(current) || collectiblesManager.isCollectiblesItem(cursor)) {
                event.setCancelled(true);

                Bukkit.getScheduler().runTask(plugin, () ->
                        collectiblesManager.giveCollectiblesItem(player));
            }
            return;
        }

        // BLOCK NUMBER KEY SWAP (1–9)
        if (event.getClick() == ClickType.NUMBER_KEY) {
            if (event.getHotbarButton() == CollectiblesManager.HOTBAR_SLOT) {
                event.setCancelled(true);
                return;
            }
        }

        // BLOCK MOVING THE CHEST ANYWHERE
        if (collectiblesManager.isCollectiblesItem(current) || collectiblesManager.isCollectiblesItem(cursor)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () ->
                    collectiblesManager.giveCollectiblesItem(player));
            return;
        }

        // BLOCK ALL CLICKING INSIDE OUR GUI
        if (topInv != null && topInv.getHolder() instanceof CollectiblesGUIHolder holder) {
            event.setCancelled(true);

            if (clickedInv == null || clickedInv.getType() != InventoryType.CHEST)
                return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= topInv.getSize())
                return;

            ItemStack clicked = topInv.getItem(slot);
            if (clicked == null || clicked.getType() == Material.AIR)
                return;

            String strippedName = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()
                    ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName()) : "";

            Page page = holder.getPage();

            // MAIN PAGE CATEGORIES
            if (page == Page.MAIN) {
                if (clicked.getType() == Material.BLAZE_POWDER && strippedName.equalsIgnoreCase("Particles")) {
                    collectiblesManager.openParticlesGUI(player);
                } else if (clicked.getType() == Material.FIREWORK_ROCKET && strippedName.equalsIgnoreCase("Win Effects")) {
                    collectiblesManager.openWinEffectsGUI(player);
                }
            }

            // SUBPAGE BACK BUTTON
            if (page == Page.PARTICLES || page == Page.WIN_EFFECTS) {
                if (clicked.getType() == Material.ARROW && strippedName.equalsIgnoreCase("Back")) {
                    collectiblesManager.openMainGUI(player);
                }
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (collectiblesManager.isCollectiblesItem(dropped)) {
            event.setCancelled(true);

            Bukkit.getScheduler().runTask(plugin,
                    () -> collectiblesManager.giveCollectiblesItem(event.getPlayer()));
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (collectiblesManager.isCollectiblesItem(event.getMainHandItem())
                || collectiblesManager.isCollectiblesItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}
