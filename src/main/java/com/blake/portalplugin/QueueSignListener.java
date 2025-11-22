package com.blake.portalplugin.listeners;

import com.blake.portalplugin.queues.GameQueue;
import com.blake.portalplugin.queues.GameQueueManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class QueueSignListener implements Listener {

    private final GameQueueManager queueManager;

    public QueueSignListener(GameQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent e) {

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Block block = e.getClickedBlock();
        if (block == null) {
            return;
        }

        // Only respond to our custom queue signs
        if (!block.hasMetadata("queue_sign")) {
            return;
        }

        e.setCancelled(true); // prevent editing / breaking the sign

        String game = block.getMetadata("queue_sign").get(0).asString();
        Player player = e.getPlayer();

        if (!queueManager.queueExists(game)) {
            player.sendMessage("This queue no longer exists.");
            return;
        }

        GameQueue queue = queueManager.getQueue(game);

        // Toggle: if already in queue, remove; otherwise add and handle arena assignment
        if (queue.isPlayerQueued(player)) {
            queue.removePlayer(player);
            player.sendMessage("You left the queue for " + game + ".");
        } else {
            queue.addPlayer(player);
            player.sendMessage("You joined the queue for " + game + ".");
            queueManager.handlePlayerQueued(game);
        }
    }
}
