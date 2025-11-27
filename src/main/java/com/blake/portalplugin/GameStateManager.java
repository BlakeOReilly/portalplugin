// src/main/java/com/blake/portalplugin/GameStateManager.java
package com.blake.portalplugin;

import com.blake.portalplugin.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameStateManager implements Listener {

    private final Plugin plugin;
    private final Map<UUID, GameState> states = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<GameState, Set<String>> statePermissions = new EnumMap<>(GameState.class);

    public GameStateManager(Plugin plugin) {
        this.plugin = plugin;
        loadPermissionsFromConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ---------------------------------------------------------
    // Load permissions
    // ---------------------------------------------------------
    public void loadPermissionsFromConfig() {
        statePermissions.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("permissions");

        for (GameState gs : GameState.values()) {
            Set<String> perms = new LinkedHashSet<>();

            if (root != null) {
                List<String> list = root.getStringList(gs.name());
                if (list != null) {
                    for (String p : list) {
                        if (p != null && !p.isBlank()) perms.add(p.trim());
                    }
                }
            }
            statePermissions.put(gs, perms);
        }
    }

    // ---------------------------------------------------------
    // Internal get/set
    // ---------------------------------------------------------
    public GameState getState(UUID uuid) {
        return states.getOrDefault(uuid, GameState.HUB);
    }

    public void setState(Player player, GameState newState) {
        if (newState == null || player == null) return;

        states.put(player.getUniqueId(), newState);
        applyPermissions(player, newState);
        applyGameMode(player, newState);
        applyStateItems(player, newState);
    }

    // ---------------------------------------------------------
    // Public API
    // ---------------------------------------------------------
    public GameState getGameState(Player player) {
        return getState(player.getUniqueId());
    }

    public void setGameState(Player player, GameState state) {
        setState(player, state);
        if (plugin instanceof PortalPlugin portal) {
            ScoreboardManager sb = portal.getScoreboardManager();
            if (sb != null) sb.refreshAll();
        }
    }

    // ---------------------------------------------------------
    // Default HUB on join
    // ---------------------------------------------------------
    public void ensureDefault(Player player) {
        states.putIfAbsent(player.getUniqueId(), GameState.HUB);
        GameState state = getState(player.getUniqueId());
        applyPermissions(player, state);
        applyGameMode(player, state);
        applyStateItems(player, state);
    }

    // ---------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------
    private void applyPermissions(Player player, GameState state) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                old.getPermissions().keySet().forEach(old::unsetPermission);
                player.removeAttachment(old);
            } catch (Throwable ignored) {}
        }

        PermissionAttachment att = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), att);

        Set<String> perms = statePermissions.getOrDefault(state, Collections.emptySet());
        for (String perm : perms) att.setPermission(perm, true);

        player.recalculatePermissions();
    }

    // ---------------------------------------------------------
    // GameModes
    // ---------------------------------------------------------
    private void applyGameMode(Player player, GameState state) {
        switch (state) {
            case HUB:
            case ARENA:
                if (player.getGameMode() != GameMode.ADVENTURE)
                    player.setGameMode(GameMode.ADVENTURE);
                break;

            case SPLEEF:
                if (player.getGameMode() != GameMode.SURVIVAL)
                    player.setGameMode(GameMode.SURVIVAL);
                break;

            case ADMIN:
                if (player.getGameMode() != GameMode.CREATIVE)
                    player.setGameMode(GameMode.CREATIVE);
                break;
        }
    }

    // ---------------------------------------------------------
    // HUD Items
    // ---------------------------------------------------------
    private void applyStateItems(Player player, GameState state) {
        if (!(plugin instanceof PortalPlugin portal)) return;

        CollectiblesManager collectiblesManager = portal.getCollectiblesManager();
        NavigationManager navigationManager = portal.getNavigationManager();
        CosmeticsManager cosmeticsManager = portal.getCosmeticsManager();

        switch (state) {

            case HUB:
                if (collectiblesManager != null) collectiblesManager.giveCollectiblesItem(player);
                if (navigationManager != null) navigationManager.giveNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.giveCosmeticsItem(player);
                break;

            case ARENA:
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.giveCosmeticsItem(player);
                break;

            case SPLEEF:
                // Remove everything — absolutely no cosmetics in spleef
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);

                // Force-remove again one tick later (Spigot overwrite fix)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);
                });
                break;

            default:
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);
                break;
        }
    }

    // ---------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------
    public void clear(Player player) {
        states.remove(player.getUniqueId());

        PermissionAttachment att = attachments.remove(player.getUniqueId());
        if (att != null) {
            try {
                att.getPermissions().keySet().forEach(att::unsetPermission);
                player.removeAttachment(att);
            } catch (Throwable ignored) {}
        }
        player.recalculatePermissions();
    }

    public void clearAllOnline() {
        for (UUID uuid : new ArrayList<>(attachments.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) clear(p);
        }
        states.clear();
        attachments.clear();
    }

    // ---------------------------------------------------------
    // Damage/Hunger Protection
    // ---------------------------------------------------------
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player)
            event.setCancelled(true);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20);
        }
    }
}
