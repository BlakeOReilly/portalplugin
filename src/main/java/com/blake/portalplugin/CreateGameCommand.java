package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.queues.GameQueueManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CreateGameCommand implements CommandExecutor {

    private final GameQueueManager queueManager;
    private final PortalPlugin plugin;

    public CreateGameCommand(GameQueueManager queueManager, PortalPlugin plugin) {
        this.queueManager = queueManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length != 1) {
            sender.sendMessage("Usage: /creategame <gamename>");
            return true;
        }

        String gameName = args[0].toLowerCase();

        if (queueManager.queueExists(gameName)) {
            sender.sendMessage("This game already has a queue.");
            return true;
        }

        queueManager.createQueue(gameName);
        sender.sendMessage("Created game queue: " + gameName);

        // SAVE IMMEDIATELY
        plugin.getConfig().set("queues", queueManager.getQueueNames());
        plugin.saveConfig();

        return true;
    }
}
