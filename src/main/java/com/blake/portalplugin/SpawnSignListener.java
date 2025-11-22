package com.blake.portalplugin.listeners;

import com.blake.portalplugin.HubSpawnManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class SpawnSignListener implements Listener {

    private final HubSpawnManager hubSpawnManager;

    public SpawnSignListener(HubSpawnManager hubSpawnManager) {
        this.hubSpawnManager = hubSpawnManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Block block = e.getClickedBlock();

        // Only signs created by /createspawnsign
        if (!block.hasMetadata("spawn_sign")) return;

        Player p = e.getPlayer();
        Location spawn = hubSpawnManager.getHubSpawn();

        if (spawn != null) {
            p.teleport(spawn);
            p.sendMessage("§aTeleported to spawn.");
        }
    }
}
