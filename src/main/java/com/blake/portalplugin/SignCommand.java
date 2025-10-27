package com.blake.portalplugin;

import org.bukkit.entity.Player;

public class SignCommand {
    private GameQueueManager queue;

    public void execute(Player p) {
        queue.addPlayerToQueue(p);
    }
}