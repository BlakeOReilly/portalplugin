package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class BlastPowerupMenuListener implements Listener {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final BlastPowerupManager powerups;

    public BlastPowerupMenuListener(PortalPlugin plugin, GameStateManager gameStateManager, BlastPowerupManager powerups) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
        this.powerups = powerups;

        // Periodic safety sync so menu stays correct
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p == null || !p.isOnline()) continue;
                    if (gameStateManager.getGameState(p) != GameState.BLAST) continue;

                    BlastPowerupMenu.sync(plugin, p, powerups);
                    if (powerups != null) powerups.applyEffects(p);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        boolean currentMenu = BlastPowerupMenu.isMenuItem(plugin, current);
        boolean cursorMenu = BlastPowerupMenu.isMenuItem(plugin, cursor);

        // Block moving menu items
        if (currentMenu || cursorMenu) {
            e.setCancelled(true);
        }

        // Block placing items into reserved menu slots
        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv != null && clickedInv.equals(p.getInventory())) {
            int slot = e.getSlot();
            if (BlastPowerupMenu.isReservedSlot(slot)) {
                e.setCancelled(true);
            }
        }

        // Block hotbar swap into/out of menu slots
        if (e.getClick() == ClickType.NUMBER_KEY) {
            int hotbar = e.getHotbarButton();
            if (hotbar >= 0) {
                ItemStack hotbarItem = p.getInventory().getItem(hotbar);
                boolean hotbarMenu = BlastPowerupMenu.isMenuItem(plugin, hotbarItem);

                if (hotbarMenu) {
                    e.setCancelled(true);
                }
                if (clickedInv != null && clickedInv.equals(p.getInventory()) && BlastPowerupMenu.isReservedSlot(e.getSlot())) {
                    e.setCancelled(true);
                }
            }
        }

        // Handle powerup purchase clicks (buttons)
        BlastPowerupType type = BlastPowerupMenu.getClickedButtonType(plugin, current);
        if (type != null) {
            e.setCancelled(true);

            BlastPowerupManager.PurchaseResult res = powerups.tryPurchase(p, type);
            if (res == BlastPowerupManager.PurchaseResult.NO_DIAMOND) {
                p.sendMessage("§c[BLAST] You need a diamond to buy: " + type.getDisplay());
                play(p, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.2f);
            } else if (res == BlastPowerupManager.PurchaseResult.MAXED) {
                p.sendMessage("§e[BLAST] Already maxed: " + type.getDisplay() + " (3/3)");
                play(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            } else {
                int now = powerups.getStacks(p, type);
                p.sendMessage("§a[BLAST] Purchased " + type.getDisplay() + " §7(" + now + "/3)");
                play(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.6f);
            }

            BlastPowerupMenu.sync(plugin, p, powerups);
            return;
        }

        // After any inventory interaction, re-sync (next tick) to prevent edge cases
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (gameStateManager.getGameState(p) != GameState.BLAST) return;
            BlastPowerupMenu.sync(plugin, p, powerups);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        int topSize = e.getView().getTopInventory().getSize();

        for (int slot : BlastPowerupMenu.getReservedSlots()) {
            int raw = topSize + slot;
            if (e.getRawSlots().contains(raw)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        ItemStack drop = e.getItemDrop() != null ? e.getItemDrop().getItemStack() : null;
        if (BlastPowerupMenu.isMenuItem(plugin, drop)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        ItemStack main = e.getMainHandItem();
        ItemStack off = e.getOffHandItem();

        if (BlastPowerupMenu.isMenuItem(plugin, main) || BlastPowerupMenu.isMenuItem(plugin, off)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        // prevent memory leak
        if (powerups != null) powerups.clear(p);
    }

    private void play(Player p, Sound s, float v, float pitch) {
        try { p.playSound(p.getLocation(), s, v, pitch); } catch (Throwable ignored) {}
    }
}
