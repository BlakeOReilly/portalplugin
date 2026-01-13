package com.blake.portalplugin.commands;

import com.blake.portalplugin.MinigameQueueManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ResumeCountdownCommand implements CommandExecutor {

    private final MinigameQueueManager manager;

    public ResumeCountdownCommand(MinigameQueueManager manager) {
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

        if (!manager.isCountdownPaused()) {
            sender.sendMessage("The countdown is not paused.");
            return true;
        }

        manager.resumeCountdown();
        manager.broadcastToQueued("§a[BLAST] Countdown resumed.");
        sender.sendMessage("Countdown resumed.");
        return true;
    }
}
