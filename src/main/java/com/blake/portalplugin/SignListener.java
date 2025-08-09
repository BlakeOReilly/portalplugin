package com.blake.portalplugin;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignListener implements Listener {
    private final GameQueueManager queueManager;
    private final GameStateManager stateManager;

    public SignListener(GameQueueManager queueManager, GameStateManager stateManager) {
        this.queueManager = queueManager;
        this.stateManager = stateManager;
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (!(b.getState() instanceof Sign sign)) return;

        String line0 = ChatColor.stripColor(sign.getLine(0)).trim();
        String line1 = ChatColor.stripColor(sign.getLine(1)).trim().toLowerCase();

        boolean headerOk = line0.equalsIgnoreCase("[Queue]") || line0.equalsIgnoreCase("[Join]");
        boolean gameOk = line1.equalsIgnoreCase("spleef");

        if (!headerOk || !gameOk) return;

        Player p = e.getPlayer();
        int pos = queueManager.addPlayerToQueue(p);
        p.sendMessage("§aJoined the Spleef queue. Position: §f" + pos);
        e.setCancelled(true);
    }
}
