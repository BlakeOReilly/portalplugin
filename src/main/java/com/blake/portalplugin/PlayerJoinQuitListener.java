package com.blake.portalplugin.listeners;

import com.blake.portalplugin.GameStateManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final GameStateManager manager;

    public PlayerJoinQuitListener(GameStateManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.ensureDefault(e.getPlayer()); // defaults to HUB on first sight
        e.getPlayer().sendMessage(ChatColor.GRAY + "[GameState] Defaulted to HUB.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Optional: keep state in memory across sessions by NOT clearing here.
        // We will clear only attachments to avoid leaks.
        manager.clear(e.getPlayer());
    }
}
