package com.blake.portalplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class BlastListener implements Listener {

    private final BlastMinigameManager blast;

    public BlastListener(BlastMinigameManager blast) {
        this.blast = blast;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (blast == null) return;
        blast.handlePlayerQuit(e.getPlayer());
    }
}
