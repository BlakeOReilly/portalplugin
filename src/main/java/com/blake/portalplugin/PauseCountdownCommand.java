package com.blake.portalplugin.commands;

import com.blake.portalplugin.MinigameQueueManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PauseCountdownCommand implements CommandExecutor {

    private final MinigameQueueManager manager;

    public PauseCountdownCommand(MinigameQueueManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (manager == null || !manager.isBlastMinigameQueueActive()) {
            sender.sendMessage("BLAST queue is not active on this server.");
            return true;
        }

        if (!manager.hasCountdown()) {
            sender.sendMessage("No countdown is currently running.");
            return true;
        }

        if (manager.isCountdownPaused()) {
            sender.sendMessage("The countdown is already paused.");
            return true;
        }

        manager.pauseCountdown();
        manager.broadcastToQueued("§e[BLAST] Countdown paused.");
        sender.sendMessage("Countdown paused.");
        return true;
    }
}
