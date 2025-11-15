package com.blake.portalplugin.commands;

import com.blake.portalplugin.queues.GameQueue;
import com.blake.portalplugin.queues.GameQueueManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JoinGameCommand implements CommandExecutor {

    private final GameQueueManager queueManager;

    public JoinGameCommand(GameQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can join queues.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("Usage: /join <game>");
            return true;
        }

        Player player = (Player) sender;
        String game = args[0].toLowerCase();

        if (!queueManager.queueExists(game)) {
            sender.sendMessage("That game does not exist.");
            return true;
        }

        GameQueue queue = queueManager.getQueue(game);

        if (queue.isPlayerQueued(player)) {
            // Toggle off: remove from queue
            queue.removePlayer(player);
            player.sendMessage("You left the queue for " + game + ".");
            return true;
        }

        queue.addPlayer(player);
        player.sendMessage("You joined the queue for " + game + ".");

        // New: handle arena assignment when queue has > 1 player
        queueManager.handlePlayerQueued(game, player);

        return true;
    }
}
