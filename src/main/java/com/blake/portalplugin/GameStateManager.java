// src/main/java/com/blake/portalplugin/GameStateManager.java
package com.blake.portalplugin;

import com.blake.portalplugin.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameStateManager {

    private final Plugin plugin;
    private final Map<UUID, GameState> states = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<GameState, Set<String>> statePermissions = new EnumMap<>(GameState.class);

    public GameStateManager(Plugin plugin) {
        this.plugin = plugin;
        loadPermissionsFromConfig();
    }

    // ---------------------------------------------------------
    // Load state-based permissions from config.yml
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
                        if (p != null && !p.isBlank()) {
                            perms.add(p.trim());
                        }
                    }
                }
            }

            statePermissions.put(gs, perms);
        }
    }

    // ---------------------------------------------------------
    // Existing API: internal storage (UUID-based)
    // ---------------------------------------------------------
    public GameState getState(UUID uuid) {
        return states.getOrDefault(uuid, GameState.HUB);
    }

    public void setState(Player player, GameState newState) {
        if (newState == null || player == null) return;
        states.put(player.getUniqueId(), newState);
        applyPermissions(player, newState);
        applyGameMode(player, newState);
    }

    // ---------------------------------------------------------
    // Player-based methods
    // ---------------------------------------------------------
    public GameState getGameState(Player player) {
        return getState(player.getUniqueId());
    }

    /**
     * When a player's game state changes:
     *  - update stored state
     *  - update permissions and gamemode
     *  - refresh scoreboards for ALL players so "Players Joined" etc. stay in sync
     */
    public void setGameState(Player player, GameState state) {
        setState(player, state);

        // Refresh all scoreboards so that arena-related counts (Players Joined / Players Left)
        // are correct for everyone, not just the player whose state changed.
        if (plugin instanceof PortalPlugin portal) {
            ScoreboardManager sb = portal.getScoreboardManager();
            if (sb != null) {
                sb.refreshAll();
            }
        }
    }

    // ---------------------------------------------------------
    // Ensure player begins with default HUB state
    // ---------------------------------------------------------
    public void ensureDefault(Player player) {
        states.putIfAbsent(player.getUniqueId(), GameState.HUB);
        GameState state = getState(player.getUniqueId());
        applyPermissions(player, state);
        applyGameMode(player, state);
    }

    // ---------------------------------------------------------
    // Apply permissions whenever state changes
    // ---------------------------------------------------------
    private void applyPermissions(Player player, GameState state) {
        // Remove old attachment
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                old.getPermissions().keySet().forEach(old::unsetPermission);
                player.removeAttachment(old);
            } catch (Throwable ignored) {}
        }

        // Create a fresh attachment
        PermissionAttachment att = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), att);

        // State-specific permissions
        Set<String> perms = statePermissions.getOrDefault(state, Collections.emptySet());
        for (String perm : perms) {
            att.setPermission(perm, true);
        }

        player.recalculatePermissions();
    }

    // ---------------------------------------------------------
    // Apply GameMode whenever state changes
    // ---------------------------------------------------------
    private void applyGameMode(Player player, GameState state) {
        if (player == null || state == null) return;

        switch (state) {
            case HUB:
            case ARENA:
                if (player.getGameMode() != GameMode.ADVENTURE) {
                    player.setGameMode(GameMode.ADVENTURE);
                }
                break;

            case SPLEEF:
                if (player.getGameMode() != GameMode.SURVIVAL) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                break;

            case ADMIN: // NEW
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.setGameMode(GameMode.CREATIVE);
                }
                break;

            default:
                // Do nothing for unknown states
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
            if (p != null && p.isOnline()) {
                clear(p);
            }
        }
        states.clear();
        attachments.clear();
    }
}
