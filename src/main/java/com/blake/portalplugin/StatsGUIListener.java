package com.blake.portalplugin.listeners;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class StatsGUIListener implements Listener {

    private static final String GUI_TITLE = ChatColor.stripColor("Your Game Stats");

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {

        if (e.getView() == null || e.getView().getTitle() == null)
            return;

        String title = ChatColor.stripColor(e.getView().getTitle());

        // Check if clicking inside the stats GUI
        if (!title.equalsIgnoreCase(GUI_TITLE))
            return;

        // Cancel all interactions
        e.setCancelled(true);

        // Prevent taking item with cursor
        e.setCurrentItem(e.getCurrentItem()); // Not required but safe
    }
}
