package com.blake.portalplugin.commands;

import com.blake.portalplugin.queues.GameQueueManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class CreateSignCommand implements CommandExecutor {

    private final GameQueueManager queueManager;
    private final JavaPlugin plugin;

    public CreateSignCommand(GameQueueManager queueManager, JavaPlugin plugin) {
        this.queueManager = queueManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can create signs.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("Usage: /createsign <game>");
            return true;
        }

        Player player = (Player) sender;
        String game = args[0].toLowerCase();

        if (!queueManager.queueExists(game)) {
            sender.sendMessage("That game queue does not exist.");
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

        // ---------------------------------------------------
        // CASE 1: PLAYER IS LOOKING AT TOP OF BLOCK (UP FACE)
        // standing sign
        // ---------------------------------------------------
        if (face == BlockFace.UP) {
            signBlock = target.getRelative(BlockFace.UP);

            if (!signBlock.getType().isAir()) {
                sender.sendMessage("There is no space above that block.");
                return true;
            }

            signBlock.setType(Material.OAK_SIGN);

            BlockData data = signBlock.getBlockData();
            if (data instanceof org.bukkit.block.data.type.Sign standingSign) {
                // rotate standing sign toward player
                standingSign.setRotation(player.getFacing().getOppositeFace());
                signBlock.setBlockData(standingSign);
            }
        }

        // ---------------------------------------------------
        // CASE 2: PLAYER IS LOOKING AT WALL (NORTH/S/E/W)
        // wall sign
        // ---------------------------------------------------
        else {
            signBlock = target.getRelative(face);

            if (!signBlock.getType().isAir()) {
                sender.sendMessage("No room to place a wall sign there.");
                return true;
            }

            signBlock.setType(Material.OAK_WALL_SIGN);

            BlockData data = signBlock.getBlockData();
            if (data instanceof org.bukkit.block.data.type.WallSign wallSign) {
                wallSign.setFacing(face);
                signBlock.setBlockData(wallSign);
            }
        }

        // ---------------------------------------------------
        // SIGN TEXT
        // ---------------------------------------------------
        if (signBlock.getState() instanceof Sign sign) {
            sign.setLine(0, "§aJoin Queue");
            sign.setLine(1, game);
            sign.update();
        }

        // Metadata so clicking works
        signBlock.setMetadata("queue_sign", new FixedMetadataValue(plugin, game));

        player.sendMessage("Created " + (face == BlockFace.UP ? "standing" : "wall")
                + " sign for game: " + game);

        return true;
    }
}
