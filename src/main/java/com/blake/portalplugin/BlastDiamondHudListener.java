package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class BlastDiamondHudListener implements Listener {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final BlastDiamondSpawnerService diamondSpawnerService;

    public BlastDiamondHudListener(PortalPlugin plugin, GameStateManager gameStateManager, BlastDiamondSpawnerService diamondSpawnerService) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
        this.diamondSpawnerService = diamondSpawnerService;

        // Only run periodic HUD sync if HUD is enabled.
        if (isHudEnabled()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p == null || !p.isOnline()) continue;
                        if (gameStateManager.getGameState(p) != GameState.BLAST) continue;
                        BlastDiamondHud.sync(plugin, p);
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }
    }

    private boolean isHudEnabled() {
        try {
            // Default false => "normal diamonds" unless explicitly enabled.
            return plugin.getConfig().getBoolean("blast.diamond-hud.enabled", false);
        } catch (Throwable t) {
            return false;
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;

        Item itemEnt = e.getItem();
        ItemStack stack = itemEnt != null ? itemEnt.getItemStack() : null;

        boolean isSpawnedDiamond = (itemEnt != null && diamondSpawnerService != null && diamondSpawnerService.isTrackedSpawnedDiamond(itemEnt));

        // Keep this restriction: spawned BLAST diamonds should not be collected while NOT in BLAST.
        if (isSpawnedDiamond && gameStateManager.getGameState(p) != GameState.BLAST) {
            e.setCancelled(true);
            return;
        }

        // If HUD enabled, sync after normal pickup.
        if (isHudEnabled()
                && stack != null
                && stack.getType() == Material.DIAMOND
                && gameStateManager.getGameState(p) == GameState.BLAST) {

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && gameStateManager.getGameState(p) == GameState.BLAST) {
                    BlastDiamondHud.sync(plugin, p);
                }
            });
        }

        // If it was one of our tracked diamonds, unregister it once picked up
        if (isSpawnedDiamond) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (diamondSpawnerService != null) diamondSpawnerService.unregister(itemEnt);
            });
        }
    }

    @EventHandler
    public void onDespawn(ItemDespawnEvent e) {
        Item itemEnt = e.getEntity();
        if (itemEnt == null) return;
        if (diamondSpawnerService == null) return;

        if (diamondSpawnerService.isTrackedSpawnedDiamond(itemEnt)) {
            diamondSpawnerService.unregister(itemEnt);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!isHudEnabled()) return;

        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        // Lock HUD slot
        if (e.getClickedInventory() == p.getInventory() && BlastDiamondHud.isHudSlot(e.getSlot())) {
            e.setCancelled(true);
        }

        // Prevent moving/dropping HUD items around
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (BlastDiamondHud.isHudItem(plugin, current) || BlastDiamondHud.isHudItem(plugin, cursor)) {
            e.setCancelled(true);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && gameStateManager.getGameState(p) == GameState.BLAST) {
                BlastDiamondHud.sync(plugin, p);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!isHudEnabled()) return;

        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        int hudRaw = e.getView().getTopInventory().getSize() + BlastDiamondHud.HUD_SLOT;
        if (e.getRawSlots().contains(hudRaw)) {
            e.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && gameStateManager.getGameState(p) == GameState.BLAST) {
                BlastDiamondHud.sync(plugin, p);
            }
        });
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!isHudEnabled()) return;

        Player p = e.getPlayer();
        if (p == null) return;
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        ItemStack drop = e.getItemDrop() != null ? e.getItemDrop().getItemStack() : null;
        if (BlastDiamondHud.isHudItem(plugin, drop)) {
            e.setCancelled(true);
            return;
        }

        if (drop != null && drop.getType() == Material.DIAMOND) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && gameStateManager.getGameState(p) == GameState.BLAST) {
                    BlastDiamondHud.sync(plugin, p);
                }
            });
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!isHudEnabled()) return;

        Player p = e.getPlayer();
        if (p == null) return;
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        ItemStack main = e.getMainHandItem();
        ItemStack off = e.getOffHandItem();

        if (BlastDiamondHud.isHudItem(plugin, main) || BlastDiamondHud.isHudItem(plugin, off)) {
            e.setCancelled(true);
        }
    }
}
