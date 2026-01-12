package com.blake.portalplugin.listeners;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global old-school soup mechanic:
 * - Right-click Mushroom Stew instantly heals 7 health (3.5 hearts)
 * - Instantly consumes the soup and leaves a bowl
 * - No eating delay
 *
 * This is enabled server-wide as long as the plugin is present.
 */
public class InstantSoupListener implements Listener {

    private static final double SOUP_HEAL_AMOUNT = 7.0; // 3.5 hearts
    private static final long USE_GUARD_MS = 75L;

    private final Plugin plugin;
    private final Map<UUID, Long> lastUseMs = new ConcurrentHashMap<>();

    public InstantSoupListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSoupRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.MUSHROOM_STEW) return;

        Player player = event.getPlayer();
        if (player == null) return;

        // Guard against double-fire (main/offhand or client/server duplicate interact packets)
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Long last = lastUseMs.get(id);
        if (last != null && (now - last) < USE_GUARD_MS) return;
        lastUseMs.put(id, now);

        double maxHealth = 20.0;
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }

        double current = player.getHealth();

        // Old soup servers typically do not consume soup if you are already full HP.
        if (current >= maxHealth) {
            event.setCancelled(true); // prevent eating animation + prevent wasting soup
            return;
        }

        // Instant heal + consume
        event.setCancelled(true);

        double newHealth = Math.min(maxHealth, current + SOUP_HEAL_AMOUNT);
        player.setHealth(newHealth);

        // Replace soup with bowl in the SAME hand used
        EquipmentSlot hand = event.getHand();
        ItemStack bowl = new ItemStack(Material.BOWL, 1);

        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(bowl);
        } else {
            // Default to main hand if null/unknown
            player.getInventory().setItemInMainHand(bowl);
        }

        player.updateInventory();
    }
}
