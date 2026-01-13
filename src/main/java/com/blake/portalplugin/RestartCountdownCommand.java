package com.blake.portalplugin.commands;

import com.blake.portalplugin.MinigameQueueManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RestartCountdownCommand implements CommandExecutor {

    private static final int RESTART_SECONDS = 30;

    private final MinigameQueueManager manager;

    public RestartCountdownCommand(MinigameQueueManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (manager == null || !manager.isBlastMinigameQueueActive()) {
            sender.sendMessage("BLAST queue is not active on this server.");
            return true;
        }

        if (manager.restartCountdown(RESTART_SECONDS)) {
            manager.broadcastToQueued("§a[BLAST] Countdown reset to " + RESTART_SECONDS + " seconds.");
            sender.sendMessage("Countdown reset to " + RESTART_SECONDS + " seconds.");
        } else {
            sender.sendMessage("Not enough players to restart the countdown.");
        }
        return true;
    }
}
