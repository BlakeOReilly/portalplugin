package com.blake.portalplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Optional helper: /sign queue -> join the queue directly */
public class SignCommand implements CommandExecutor {
    private final GameQueueManager queue;

    public SignCommand(GameQueueManager queue) {
        this.queue = queue;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player p)) return true;
        queue.addPlayerToQueue(p);
        p.sendMessage("Joined queue via command.");
        return true;
    }
}
