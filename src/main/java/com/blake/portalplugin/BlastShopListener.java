package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BlastShopListener implements Listener {

    private static final String TITLE = "§6§lBLAST SHOP";

    private final PortalPlugin plugin;
    private final GameStateManager gsm;

    public BlastShopListener(PortalPlugin plugin, GameStateManager gsm) {
        this.plugin = plugin;
        this.gsm = gsm;
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent e) {
        if (plugin.getBlastMinigameManager() != null && plugin.getBlastMinigameManager().isBlastShopNpc(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager)) return;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;

        if (!bm.isBlastShopNpc(e.getRightClicked())) return;

        Player p = e.getPlayer();
        if (p == null) return;

        e.setCancelled(true);

        if (gsm.getGameState(p) != GameState.BLAST || !bm.isParticipant(p)) {
            p.sendMessage("§c[BLAST] You must be in the BLAST game to use the shop.");
            return;
        }

        openShop(p);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    private void openShop(Player p) {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null) return;

        BlastTeam team = bm.getTeam(p);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Armor (1 token each)
        inv.setItem(10, BlastShopItems.makeShopItem(plugin, Material.IRON_HELMET, 1, "§fIron Helmet", "IRON_HELM", 1, null));
        inv.setItem(11, BlastShopItems.makeShopItem(plugin, Material.IRON_CHESTPLATE, 1, "§fIron Chestplate", "IRON_CHEST", 1, null));
        inv.setItem(12, BlastShopItems.makeShopItem(plugin, Material.IRON_LEGGINGS, 1, "§fIron Pants", "IRON_LEGS", 1, null));
        inv.setItem(13, BlastShopItems.makeShopItem(plugin, Material.IRON_BOOTS, 1, "§fIron Boots", "IRON_BOOTS", 1, null));

        // Team wool stack (1 token)
        Material woolMat = (team != null) ? BlastItems.getTeamWool(team) : Material.WHITE_WOOL;
        inv.setItem(14, BlastShopItems.makeShopItem(plugin, woolMat, 64, "§fTeam Wool Stack", "TEAM_WOOL", 1,
                List.of("§7Gives §f64§7 blocks")));

        // Instant wall (1 token) - custom item (brick)
        inv.setItem(15, BlastShopItems.makeShopItem(plugin, Material.BRICKS, 1, "§fInstant Wall", "INSTANT_WALL", 1,
                List.of("§7Right-click to place")));

        // Fireball (2 tokens) - fire charge
        inv.setItem(16, BlastShopItems.makeShopItem(plugin, Material.FIRE_CHARGE, 1, "§cFireball", "FIREBALL", 2,
                List.of("§7Right-click to launch")));

        // Add team life (apple) 5 tokens
        inv.setItem(19, BlastShopItems.makeShopItem(plugin, Material.APPLE, 1, "§a+1 Team Life", "TEAM_LIFE", 5,
                List.of("§7Right-click to use")));

        // Tracker compass (3 tokens)
        inv.setItem(21, BlastShopItems.makeShopItem(plugin, Material.COMPASS, 1, "§eTracker Compass", "TRACKER", 3,
                List.of("§7Right-click to track")));

        // Homing missile (piston) 10 tokens
        inv.setItem(23, BlastShopItems.makeShopItem(plugin, Material.PISTON, 1, "§dHoming Missile", "HOMING", 10,
                List.of("§7Right-click to fire")));

        // Boom Slingshot (bow) 5 tokens
        inv.setItem(24, BlastShopItems.makeShopItem(plugin, Material.BOW, 1, "§aBoom Slingshot", "BOOM_SLINGSHOT", 5,
                List.of("§7Explodes on impact")));

        // Ender Soar (ender pearl) 3 tokens
        inv.setItem(25, BlastShopItems.makeShopItem(plugin, Material.ENDER_PEARL, 1, "§bEnder Soar", "ENDER_SOAR", 3,
                List.of("§7Ride the pearl", "§7Cyan blast on landing")));

        // Tunneler (shears) 2 tokens
        inv.setItem(26, BlastShopItems.makeShopItem(plugin, Material.SHEARS, 1, "§fTunneler", "TUNNELER", 2,
                List.of("§7Creates a 1x2 wool tunnel")));

        p.openInventory(inv);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null || !TITLE.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;

        if (gsm.getGameState(p) != GameState.BLAST || !bm.isParticipant(p)) {
            p.closeInventory();
            return;
        }

        String id = BlastShopItems.getShopId(plugin, clicked);
        int cost = BlastShopItems.getShopCost(plugin, clicked);
        if (id == null || cost <= 0) return;

        if (!bm.trySpendElimTokens(p, cost)) {
            p.sendMessage("§c[BLAST] Not enough Elim Tokens.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }

        givePurchase(p, id);

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        // Keep the shop open (more convenient)
        openShop(p);
    }

    private void givePurchase(Player p, String id) {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        BlastTeam team = (bm != null) ? bm.getTeam(p) : null;

        switch (id) {
            case "IRON_HELM" -> giveArmor(p, new ItemStack(Material.IRON_HELMET), ArmorSlot.HELMET);
            case "IRON_CHEST" -> giveArmor(p, new ItemStack(Material.IRON_CHESTPLATE), ArmorSlot.CHEST);
            case "IRON_LEGS" -> giveArmor(p, new ItemStack(Material.IRON_LEGGINGS), ArmorSlot.LEGS);
            case "IRON_BOOTS" -> giveArmor(p, new ItemStack(Material.IRON_BOOTS), ArmorSlot.BOOTS);

            case "TEAM_WOOL" -> {
                Material wool = (team != null) ? BlastItems.getTeamWool(team) : Material.WHITE_WOOL;
                p.getInventory().addItem(new ItemStack(wool, 64));
                p.updateInventory();
            }

            case "INSTANT_WALL" -> {
                ItemStack it = BlastShopItems.makeShopItem(plugin, Material.BRICKS, 1, "§fInstant Wall", "INSTANT_WALL", 1, null);
                p.getInventory().addItem(it);
                p.updateInventory();
            }

            case "FIREBALL" -> {
                ItemStack it = BlastShopItems.makeShopItem(plugin, Material.FIRE_CHARGE, 1, "§cFireball", "FIREBALL", 2, null);
                p.getInventory().addItem(it);
                p.updateInventory();
            }

            case "TEAM_LIFE" -> {
                ItemStack it = BlastShopItems.makeShopItem(plugin, Material.APPLE, 1, "§a+1 Team Life", "TEAM_LIFE", 5, null);
                p.getInventory().addItem(it);
                p.updateInventory();
            }

            case "TRACKER" -> {
                ItemStack it = BlastShopItems.makeShopItem(plugin, Material.COMPASS, 1, "§eTracker Compass", "TRACKER", 3, null);
                p.getInventory().addItem(it);
                p.updateInventory();
            }

            case "HOMING" -> {
                ItemStack it = BlastShopItems.makeShopItem(plugin, Material.PISTON, 1, "§dHoming Missile", "HOMING", 10, null);
                p.getInventory().addItem(it);
                p.updateInventory();
            }
            case "BOOM_SLINGSHOT" -> {
                ItemStack it = BlastShopItems.makeShopItem(plugin, Material.BOW, 1, "§aBoom Slingshot", "BOOM_SLINGSHOT", 5, null);
                p.getInventory().addItem(it);
                p.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                p.updateInventory();
            }
            case "ENDER_SOAR" -> {
                ItemStack it = BlastShopItems.makeShopItem(plugin, Material.ENDER_PEARL, 1, "§bEnder Soar", "ENDER_SOAR", 3, null);
                p.getInventory().addItem(it);
                p.updateInventory();
            }
            case "TUNNELER" -> {
                ItemStack it = BlastShopItems.makeShopItem(plugin, Material.SHEARS, 1, "§fTunneler", "TUNNELER", 2, null);
                p.getInventory().addItem(it);
                p.updateInventory();
            }
        }
    }

    private void giveArmor(Player p, ItemStack armor, ArmorSlot slot) {
        var inv = p.getInventory();

        switch (slot) {
            case HELMET -> {
                if (inv.getHelmet() == null || inv.getHelmet().getType().isAir()) inv.setHelmet(armor);
                else inv.addItem(armor);
            }
            case CHEST -> {
                if (inv.getChestplate() == null || inv.getChestplate().getType().isAir()) inv.setChestplate(armor);
                else inv.addItem(armor);
            }
            case LEGS -> {
                if (inv.getLeggings() == null || inv.getLeggings().getType().isAir()) inv.setLeggings(armor);
                else inv.addItem(armor);
            }
            case BOOTS -> {
                if (inv.getBoots() == null || inv.getBoots().getType().isAir()) inv.setBoots(armor);
                else inv.addItem(armor);
            }
        }

        p.updateInventory();
    }

    private enum ArmorSlot { HELMET, CHEST, LEGS, BOOTS }
}
