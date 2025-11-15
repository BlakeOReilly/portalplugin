package com.blake.portalplugin;

import org.bukkit.Bukkit;
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
                        if (p != null && !p.isBlank()) perms.add(p.trim());
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
        if (newState == null) return;
        states.put(player.getUniqueId(), newState);
        applyPermissions(player, newState);
    }

    // ---------------------------------------------------------
    // NEW API: Player-based methods required by your new features
    // ---------------------------------------------------------
    public GameState getGameState(Player player) {
        return getState(player.getUniqueId());
    }

    public void setGameState(Player player, GameState state) {
        setState(player, state);
    }

    // ---------------------------------------------------------
    // Ensure player begins with default HUB state
    // ---------------------------------------------------------
    public void ensureDefault(Player player) {
        states.putIfAbsent(player.getUniqueId(), GameState.HUB);
        applyPermissions(player, getState(player.getUniqueId()));
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
}
