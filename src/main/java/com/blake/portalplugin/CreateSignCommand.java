package com.blake.portalplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;

public class CreateSignCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        Block target = getTargetBlock(player, 5);
        if (target == null || !(target.getState() instanceof Sign sign)) {
            player.sendMessage(ChatColor.RED + "You must be looking at a sign within 5 blocks.");
            return true;
        }

        sign.setLine(0, ChatColor.GREEN + "[Queue]");
        sign.setLine(1, ChatColor.YELLOW + "spleef");
        sign.update();

        player.sendMessage(ChatColor.GREEN + "Queue sign created: Click to join Spleef.");
        return true;
    }

    private Block getTargetBlock(Player p, int maxDistance) {
        BlockIterator it = new BlockIterator(p, maxDistance);
        while (it.hasNext()) {
            Block b = it.next();
            if (b.getType().toString().contains("SIGN")) {
                return b;
            }
        }
        return null;
    }
}
