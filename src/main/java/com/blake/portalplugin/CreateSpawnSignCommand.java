package com.blake.portalplugin.commands;

import com.blake.portalplugin.PortalPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CreateSpawnSignCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public CreateSpawnSignCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can create signs.");
            return true;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            sender.sendMessage("You must look at a block within 5 blocks.");
            return true;
        }

        BlockFace face = player.getTargetBlockFace(5);
        if (face == null) {
            sender.sendMessage("Cannot determine block face.");
            return true;
        }

        Block signBlock;

        // Standing sign (on top)
        if (face == BlockFace.UP) {

            signBlock = target.getRelative(BlockFace.UP);

            if (!signBlock.getType().isAir()) {
                sender.sendMessage("There is no space above that block.");
                return true;
            }

            signBlock.setType(Material.OAK_SIGN, false);

            BlockData data = signBlock.getBlockData();
            if (data instanceof org.bukkit.block.data.type.Sign standingData) {
                standingData.setRotation(player.getFacing());
                signBlock.setBlockData(standingData, false);
            }

        }
        // Wall sign (on side)
        else if (face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST) {

            if (!target.getType().isSolid()) {
                sender.sendMessage("The sign must be attached to a solid block.");
                return true;
            }

            signBlock = target.getRelative(face);

            if (!signBlock.getType().isAir()) {
                sender.sendMessage("No room to place a wall sign there.");
                return true;
            }

            signBlock.setType(Material.OAK_WALL_SIGN, false);

            BlockData data = signBlock.getBlockData();
            if (data instanceof WallSign wallData) {
                wallData.setFacing(face);
                signBlock.setBlockData(wallData, false);
            }

        } else {
            sender.sendMessage("You must target the top or side of a block.");
            return true;
        }

        // Write text
        if (signBlock.getState() instanceof Sign signState) {
            signState.setLine(0, "§aGo to");
            signState.setLine(1, "§eSpawn");
            signState.update();
        }

        // Register spawn sign in central plugin state (mirrors queueManager.registerSign)
        if (plugin instanceof PortalPlugin portalPlugin) {
            portalPlugin.registerSpawnSign(signBlock);
        } else {
            sender.sendMessage("Internal error: plugin instance mismatch.");
            return true;
        }

        sender.sendMessage("Created a Spawn Sign.");
        return true;
    }
}
