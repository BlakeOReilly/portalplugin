package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MinigameHubQueueListener implements Listener {

    private final PortalPlugin plugin;
    private final MinigameQueueManager manager;

    public MinigameHubQueueListener(PortalPlugin plugin, MinigameQueueManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (manager == null) return;

        // Delay 1 tick so your existing join logic (teleport/state) runs first
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isMinigameHub()) return;
            manager.handleJoin(e.getPlayer());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (manager == null) return;
        manager.handleQuit(e.getPlayer());
    }
}
