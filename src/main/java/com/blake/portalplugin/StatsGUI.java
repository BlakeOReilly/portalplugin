package com.blake.portalplugin.stats;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class StatsGUI {

    public static void open(Player viewer, List<PlayerStats> stats) {

        Inventory gui = Bukkit.createInventory(null, 27, "Your Game Stats");

        // Fill GUI with placeholders
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, filler);

        int slot = 10;

        for (PlayerStats s : stats) {

            Material icon = switch (s.getGamemode().toLowerCase()) {
                case "spleef" -> Material.DIAMOND_SHOVEL;
                case "arena" -> Material.IRON_SWORD;
                case "parkour" -> Material.FEATHER;
                default -> Material.BOOK;
            };

            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName(ChatColor.AQUA + s.getGamemode().toUpperCase());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Wins: " + ChatColor.GREEN + s.getWins());
            lore.add(ChatColor.YELLOW + "Losses: " + ChatColor.RED + s.getLosses());

            int total = s.getWins() + s.getLosses();
            double winRate = total == 0 ? 0 : (s.getWins() * 100.0 / total);

            lore.add(ChatColor.YELLOW + "Win Rate: " + ChatColor.AQUA + String.format("%.1f%%", winRate));
            meta.setLore(lore);

            item.setItemMeta(meta);

            gui.setItem(slot, item);

            slot++;
            if (slot == 17) slot = 19; // spacing row
        }

        viewer.openInventory(gui);
    }
}
