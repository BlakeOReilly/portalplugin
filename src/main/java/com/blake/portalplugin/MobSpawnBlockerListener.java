// src/main/java/com/blake/portalplugin/listeners/MobSpawnBlockerListener.java
package com.blake.portalplugin.listeners;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

public class MobSpawnBlockerListener implements Listener {

    private final Plugin plugin;
    private final GameStateManager gameStateManager;

    public MobSpawnBlockerListener(Plugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event == null) return;

        // Optional enable switch
        if (!plugin.getConfig().getBoolean("mob-spawns.enabled", true)) return;

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();

        // Allow custom/plugin spawns if you want (set to false to block everything)
        boolean blockCustom = plugin.getConfig().getBoolean("mob-spawns.block-custom", false);
        if (!blockCustom && (reason == CreatureSpawnEvent.SpawnReason.CUSTOM || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG)) {
            return;
        }

        // Block “natural” world spawns (common causes)
        switch (reason) {
            case NATURAL,
                 CHUNK_GEN,
                 JOCKEY,
                 REINFORCEMENTS,
                 PATROL,
                 RAID,
                 VILLAGE_DEFENSE,
                 VILLAGE_INVASION,
                 BREEDING,
                 EGG,
                 NETHER_PORTAL,
                 SLIME_SPLIT,
                 DROWNED,
                 MOUNT,
                 TRAP,
                 BEEHIVE,
                 DEFAULT -> {

                // Optional: only block in specific game states
                // If you want it global, leave this as-is (always cancel)
                if (plugin.getConfig().getBoolean("mob-spawns.only-block-in-hub-arena", false)) {
                    Player nearest = event.getLocation() != null && event.getLocation().getWorld() != null
                            ? event.getLocation().getWorld().getNearbyPlayers(event.getLocation(), 128).stream().findFirst().orElse(null)
                            : null;

                    if (nearest != null) {
                        GameState gs = gameStateManager.getGameState(nearest);
                        if (gs != GameState.HUB && gs != GameState.ARENA) return;
                    }
                }

                event.setCancelled(true);
            }
        }
    }
}