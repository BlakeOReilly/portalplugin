package com.blake.portalplugin.commands;

import com.blake.portalplugin.queues.GameQueueManager;
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

        // ======================================================
        // Standing Sign (on top of block)
        // ======================================================
        if (face == BlockFace.UP) {

            signBlock = target.getRelative(BlockFace.UP);

            if (!signBlock.getType().isAir()) {
                sender.sendMessage("There is no space above that block.");
                return true;
            }

            signBlock.setType(Material.OAK_SIGN, false);

            BlockData data = signBlock.getBlockData();
            if (data instanceof org.bukkit.block.data.type.Sign standingData) {
                // Simple orientation: face the same way as the player
                BlockFace playerFace = player.getFacing();
                standingData.setRotation(playerFace);
                signBlock.setBlockData(standingData, false);
            }

        }

        // ======================================================
        // Wall Sign (on side of block)
        // ======================================================
        else if (face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST) {

            // For a wall sign to stay, the block you click must be solid.
            if (!target.getType().isSolid()) {
                sender.sendMessage("The sign must be attached to a solid block.");
                return true;
            }

            // The sign block goes in front of the face we clicked.
            // Example: click EAST face -> sign is at target.getRelative(EAST)
            signBlock = target.getRelative(face);

            if (!signBlock.getType().isAir()) {
                sender.sendMessage("No room to place a wall sign there.");
                return true;
            }

            signBlock.setType(Material.OAK_WALL_SIGN, false);

            BlockData data = signBlock.getBlockData();
            if (data instanceof WallSign wallData) {
                // For WallSign, facing is the direction you look when reading the sign.
                // The supporting block is behind it at signBlock.getRelative(facing.getOppositeFace()).
                // Since we placed the sign at target.getRelative(face), we want the front of the sign
                // to face the same direction as 'face'.
                wallData.setFacing(face);
                signBlock.setBlockData(wallData, false);
            }

        }

        // Invalid face (e.g. clicking bottom of block)
        else {
            sender.sendMessage("You must target the top or side of a block.");
            return true;
        }

        // ======================================================
        // Write Text to Sign
        // ======================================================
        if (signBlock.getState() instanceof Sign signState) {
            signState.setLine(0, "§aJoin Queue");
            signState.setLine(1, game);
            signState.update();
        }

        // ======================================================
        // Persist Sign Location (for restart)
        // ======================================================
        queueManager.registerSign(signBlock, game);
        // Note: the actual config write of "queue-signs" happens in PortalPlugin.onDisable(),
        // where queueManager.getSignEntries() is written to the config. This call to saveConfig()
        // will only persist existing config state, but is harmless.
        plugin.saveConfig();

        // ======================================================
        // Metadata for click detection
        // ======================================================
        signBlock.setMetadata("queue_sign", new FixedMetadataValue(plugin, game));

        sender.sendMessage("Created a queue sign for: " + game);
        return true;
    }
}
