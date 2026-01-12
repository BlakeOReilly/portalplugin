package com.blake.portalplugin;

import com.blake.portalplugin.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameStateManager implements Listener {

    private final Plugin plugin;
    private final Map<UUID, GameState> states = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<GameState, Set<String>> statePermissions = new EnumMap<>(GameState.class);

    private final NamespacedKey kitKey;
    private static final String KIT_PVP = "pvp";
    private static final String KIT_SUMO = "sumo";
    private static final String KIT_BLAST_BASIC = "blast_basic";

    private final Map<UUID, Double> originalAttackSpeed = new ConcurrentHashMap<>();

    // ===== BLAST Powerup UI (main inventory, not hotbar) =====
    private final NamespacedKey blastPowerupUiKey;
    private final NamespacedKey blastPowerupUiIndicatorKey;

    private static final int UI_MAX_STACKS = 3;

    // Powerup items go in these "base" slots; indicator is base+1 (to the right).
    // These are MAIN inventory slots (9..35).
    private static final List<Integer> DEFAULT_UI_BASE_SLOTS = List.of(9, 11, 13, 15, 17, 19, 21, 23, 25, 27);

    private static final List<BlastPowerupType> UI_POWERUPS = List.of(
            BlastPowerupType.SPEED,
            BlastPowerupType.JUMP,
            BlastPowerupType.BLAST_SPEED,
            BlastPowerupType.BLASTER_DAMAGE,
            BlastPowerupType.DASH,
            BlastPowerupType.KNOCKBACK,
            BlastPowerupType.SLOW_SHOT,
            BlastPowerupType.BLIND_SHOT,
            BlastPowerupType.MARK_TARGET
    );

    public GameStateManager(Plugin plugin) {
        this.plugin = plugin;
        this.kitKey = new NamespacedKey(plugin, "portalplugin_kit");
        this.blastPowerupUiKey = new NamespacedKey(plugin, "blast_powerup_ui");
        this.blastPowerupUiIndicatorKey = new NamespacedKey(plugin, "blast_powerup_ui_ind");

        loadPermissionsFromConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

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

    public GameState getState(UUID uuid) {
        return states.getOrDefault(uuid, GameState.HUB);
    }

    public void setState(Player player, GameState newState) {
        if (newState == null || player == null) return;

        UUID id = player.getUniqueId();
        GameState oldState = getState(id);

        // If leaving BLAST -> always clear BLAST powerup effects + stacks.
        if (oldState == GameState.BLAST && newState != GameState.BLAST) {
            BlastPowerupManager pm = resolvePowerupManager();
            if (pm != null) {
                try { pm.reset(player); } catch (Throwable ignored) {}
            } else {
                // best-effort cleanup even if manager isn't available
                try { player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED); } catch (Throwable ignored) {}
                try { player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST); } catch (Throwable ignored) {}
            }
            clearBlastPowerupUi(player);
        }

        states.put(id, newState);

        applyPermissions(player, newState);
        applyGameMode(player, newState);
        applyStateItems(player, newState);

        applyHungerRules(player, newState);
        applyCombatSwingRules(player, newState);

        // If entering BLAST -> sync UI and force effects to match purchased stacks.
        if (newState == GameState.BLAST) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (getGameState(player) != GameState.BLAST) return;

                syncBlastPowerupUi(player);

                BlastPowerupManager pm = resolvePowerupManager();
                if (pm != null) {
                    try { pm.applyEffects(player); } catch (Throwable ignored) {}
                }

                player.updateInventory();
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                clearBlastPowerupUi(player);
                player.updateInventory();
            });
        }
    }

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

    public GameState mapGameNameToState(String gameName) {
        if (gameName == null) return GameState.ARENA;
        String g = gameName.trim().toLowerCase(Locale.ROOT);

        return switch (g) {
            case "pvp" -> GameState.PVP;
            case "spleef" -> GameState.SPLEEF;
            case "sumo" -> GameState.SUMO;
            case "blast" -> GameState.BLAST;
            default -> GameState.ARENA;
        };
    }

    public void ensureDefault(Player player) {
        states.putIfAbsent(player.getUniqueId(), GameState.HUB);
        GameState state = getState(player.getUniqueId());

        applyPermissions(player, state);
        applyGameMode(player, state);
        applyStateItems(player, state);
        applyHungerRules(player, state);
        applyCombatSwingRules(player, state);

        if (state == GameState.BLAST) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (getGameState(player) != GameState.BLAST) return;

                syncBlastPowerupUi(player);

                BlastPowerupManager pm = resolvePowerupManager();
                if (pm != null) {
                    try { pm.applyEffects(player); } catch (Throwable ignored) {}
                }

                player.updateInventory();
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                clearBlastPowerupUi(player);
                player.updateInventory();
            });
        }
    }

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

    private void applyGameMode(Player player, GameState state) {
        switch (state) {
            case HUB, ARENA -> {
                if (player.getGameMode() != GameMode.ADVENTURE) player.setGameMode(GameMode.ADVENTURE);
            }
            case SPLEEF, PVP, SUMO, BLAST -> {
                if (player.getGameMode() != GameMode.SURVIVAL) player.setGameMode(GameMode.SURVIVAL);
            }
            case ADMIN -> {
                if (player.getGameMode() != GameMode.CREATIVE) player.setGameMode(GameMode.CREATIVE);
            }
        }
    }

    private void applyStateItems(Player player, GameState state) {
        if (!(plugin instanceof PortalPlugin portal)) return;

        CollectiblesManager collectiblesManager = portal.getCollectiblesManager();
        NavigationManager navigationManager = portal.getNavigationManager();
        CosmeticsManager cosmeticsManager = portal.getCosmeticsManager();

        if (state != GameState.PVP) removeKitItems(player, KIT_PVP);
        if (state != GameState.SUMO) removeKitItems(player, KIT_SUMO);
        if (state != GameState.BLAST) removeKitItems(player, KIT_BLAST_BASIC);

        if (state != GameState.BLAST) {
            clearBlastPowerupUi(player);
        }

        switch (state) {

            case HUB -> {
                hardResetPlayerInventory(player);

                if (collectiblesManager != null) collectiblesManager.giveCollectiblesItem(player);
                if (navigationManager != null) navigationManager.giveNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.giveCosmeticsItem(player);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    hardResetPlayerInventory(player);
                    if (collectiblesManager != null) collectiblesManager.giveCollectiblesItem(player);
                    if (navigationManager != null) navigationManager.giveNavigationItem(player);
                    if (cosmeticsManager != null) cosmeticsManager.giveCosmeticsItem(player);
                    player.updateInventory();
                });
            }

            case ARENA -> {
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.giveCosmeticsItem(player);
            }

            case SPLEEF -> {
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);

                hardResetPlayerInventory(player);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);
                    hardResetPlayerInventory(player);
                    player.updateInventory();
                });
            }

            case PVP -> {
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);

                applyPvpKit(player);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyPvpKit(player);
                    player.updateInventory();
                });
            }

            case SUMO -> {
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);

                applySumoKit(player);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    applySumoKit(player);
                    player.updateInventory();
                });
            }

            case BLAST -> {
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);

                applyBlastKit(player);
                syncBlastPowerupUi(player);

                // Force potion effects to match stacks on kit apply as well
                BlastPowerupManager pm = resolvePowerupManager();
                if (pm != null) {
                    try { pm.applyEffects(player); } catch (Throwable ignored) {}
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyBlastKit(player);
                    syncBlastPowerupUi(player);

                    BlastPowerupManager pm2 = resolvePowerupManager();
                    if (pm2 != null) {
                        try { pm2.applyEffects(player); } catch (Throwable ignored) {}
                    }

                    player.updateInventory();
                });
            }

            default -> {
                if (collectiblesManager != null) collectiblesManager.removeCollectiblesItem(player);
                if (navigationManager != null) navigationManager.removeNavigationItem(player);
                if (cosmeticsManager != null) cosmeticsManager.removeCosmeticsItem(player);
            }
        }
    }

    private void hardResetPlayerInventory(Player player) {
        if (player == null) return;
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
    }

    private void applyBlastKit(Player player) {
        if (player == null) return;

        hardResetPlayerInventory(player);

        ItemStack basic = BlastItems.createBasicBlaster(plugin);
        tagKitItem(basic, KIT_BLAST_BASIC);
        player.getInventory().setItem(0, basic);

        player.updateInventory();
    }

    private void applyPvpKit(Player player) {
        if (player == null) return;

        hardResetPlayerInventory(player);

        Enchantment sharpness = Enchantment.getByKey(NamespacedKey.minecraft("sharpness"));
        Enchantment protection = Enchantment.getByKey(NamespacedKey.minecraft("protection"));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
        if (sharpness != null) sword.addUnsafeEnchantment(sharpness, 2);
        tagKitItem(sword, KIT_PVP);
        player.getInventory().setItem(0, sword);

        for (int slot = 1; slot <= 35; slot++) {
            ItemStack stew = new ItemStack(Material.MUSHROOM_STEW, 1);
            tagKitItem(stew, KIT_PVP);
            player.getInventory().setItem(slot, stew);
        }

        ItemStack helmet = new ItemStack(Material.IRON_HELMET, 1);
        ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE, 1);
        ItemStack legs = new ItemStack(Material.IRON_LEGGINGS, 1);
        ItemStack boots = new ItemStack(Material.IRON_BOOTS, 1);

        if (protection != null) {
            helmet.addUnsafeEnchantment(protection, 2);
            chest.addUnsafeEnchantment(protection, 2);
            legs.addUnsafeEnchantment(protection, 2);
            boots.addUnsafeEnchantment(protection, 2);
        }

        tagKitItem(helmet, KIT_PVP);
        tagKitItem(chest, KIT_PVP);
        tagKitItem(legs, KIT_PVP);
        tagKitItem(boots, KIT_PVP);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);

        player.updateInventory();
    }

    private void applySumoKit(Player player) {
        if (player == null) return;

        hardResetPlayerInventory(player);

        Enchantment knockback = Enchantment.getByKey(NamespacedKey.minecraft("knockback"));

        ItemStack stick = new ItemStack(Material.STICK, 1);
        if (knockback != null) stick.addUnsafeEnchantment(knockback, 1);
        tagKitItem(stick, KIT_SUMO);

        player.getInventory().setItem(0, stick);
        player.updateInventory();
    }

    private void tagKitItem(ItemStack item, String kitName) {
        if (item == null || item.getType().isAir()) return;
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(kitKey, PersistentDataType.STRING, kitName);
        });
    }

    private boolean isKitItem(ItemStack item, String kitName) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        return kitName.equals(val);
    }

    private void removeKitItems(Player player, String kitName) {
        if (player == null) return;

        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            if (isKitItem(contents[i], kitName)) {
                contents[i] = null;
                changed = true;
            }
        }
        player.getInventory().setContents(contents);

        ItemStack h = player.getInventory().getHelmet();
        ItemStack c = player.getInventory().getChestplate();
        ItemStack l = player.getInventory().getLeggings();
        ItemStack b = player.getInventory().getBoots();

        if (isKitItem(h, kitName)) { player.getInventory().setHelmet(null); changed = true; }
        if (isKitItem(c, kitName)) { player.getInventory().setChestplate(null); changed = true; }
        if (isKitItem(l, kitName)) { player.getInventory().setLeggings(null); changed = true; }
        if (isKitItem(b, kitName)) { player.getInventory().setBoots(null); changed = true; }

        ItemStack off = player.getInventory().getItemInOffHand();
        if (isKitItem(off, kitName)) {
            player.getInventory().setItemInOffHand(null);
            changed = true;
        }

        if (changed) player.updateInventory();
    }

    private boolean shouldDisableHunger(GameState state) {
        return state == GameState.PVP
                || state == GameState.SPLEEF
                || state == GameState.SUMO
                || state == GameState.BLAST
                || state == GameState.HUB
                || state == GameState.ARENA;
    }

    private void applyHungerRules(Player player, GameState state) {
        if (player == null || state == null) return;
        if (!shouldDisableHunger(state)) return;

        try {
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setExhaustion(0f);
        } catch (Throwable ignored) {}
    }

    private void applyCombatSwingRules(Player player, GameState state) {
        if (player == null || state == null) return;

        boolean enabled = plugin.getConfig().getBoolean("legacy-combat.enabled", true);
        if (!enabled) {
            restoreAttackSpeed(player);
            return;
        }

        if (state == GameState.PVP || state == GameState.SUMO) {
            applyHighAttackSpeed(player);
        } else {
            restoreAttackSpeed(player);
        }
    }

    private void applyHighAttackSpeed(Player player) {
        try {
            AttributeInstance inst = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (inst == null) return;

            originalAttackSpeed.putIfAbsent(player.getUniqueId(), inst.getBaseValue());

            double value = plugin.getConfig().getDouble("legacy-combat.attackSpeed", 1024.0);
            inst.setBaseValue(value);
        } catch (Throwable ignored) {}
    }

    private void restoreAttackSpeed(Player player) {
        try {
            AttributeInstance inst = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (inst == null) return;

            Double old = originalAttackSpeed.remove(player.getUniqueId());
            if (old != null) inst.setBaseValue(old);
        } catch (Throwable ignored) {}
    }

    public void clear(Player player) {
        if (player != null) restoreAttackSpeed(player);

        states.remove(player.getUniqueId());

        PermissionAttachment att = attachments.remove(player.getUniqueId());
        if (att != null) {
            try {
                att.getPermissions().keySet().forEach(att::unsetPermission);
                player.removeAttachment(att);
            } catch (Throwable ignored) {}
        }
        player.recalculatePermissions();

        if (player != null) {
            clearBlastPowerupUi(player);
            BlastPowerupManager pm = resolvePowerupManager();
            if (pm != null) {
                try { pm.reset(player); } catch (Throwable ignored) {}
            } else {
                try { player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED); } catch (Throwable ignored) {}
                try { player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST); } catch (Throwable ignored) {}
            }
        }
    }

    public void clearAllOnline() {
        for (UUID uuid : new ArrayList<>(attachments.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) clear(p);
        }
        states.clear();
        attachments.clear();
        originalAttackSpeed.clear();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        GameState state = getState(player.getUniqueId());

        if (state == GameState.PVP) return;

        if (state == GameState.SUMO) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                event.setDamage(0.0);
                return;
            }
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        GameState state = getState(player.getUniqueId());

        if (shouldDisableHunger(state)) {
            event.setCancelled(true);
            applyHungerRules(player, state);
        }
    }

    // =========================
    // BLAST POWERUP UI
    // =========================

    private boolean blastPowerupUiEnabled() {
        if (plugin.getConfig().contains("blast.powerups.ui.enabled")) {
            return plugin.getConfig().getBoolean("blast.powerups.ui.enabled", true);
        }
        if (plugin.getConfig().contains("blast.powerups.hud.enabled")) {
            return plugin.getConfig().getBoolean("blast.powerups.hud.enabled", true);
        }
        return true;
    }

    private List<Integer> getUiBaseSlots() {
        List<Integer> slots = null;
        try {
            if (plugin.getConfig().contains("blast.powerups.ui.base-slots")) {
                slots = plugin.getConfig().getIntegerList("blast.powerups.ui.base-slots");
            } else if (plugin.getConfig().contains("blast.powerups.hud.slots")) {
                slots = null;
            }
        } catch (Throwable ignored) {
            slots = null;
        }

        List<Integer> base = new ArrayList<>();
        if (slots != null) {
            for (Integer s : slots) {
                if (s == null) continue;
                int v = s;
                if (v < 9 || v > 35) continue;
                if ((v % 9) == 8) continue;
                if (!base.contains(v)) base.add(v);
                if (base.size() >= UI_POWERUPS.size()) break;
            }
        }

        if (base.size() < UI_POWERUPS.size()) {
            base.clear();
            base.addAll(DEFAULT_UI_BASE_SLOTS);
        }

        return base;
    }

    private Set<Integer> getReservedUiSlots() {
        List<Integer> base = getUiBaseSlots();
        Set<Integer> out = new HashSet<>();
        for (int s : base) {
            out.add(s);
            out.add(s + 1);
        }
        return out;
    }

    private BlastPowerupManager resolvePowerupManager() {
        if (!(plugin instanceof PortalPlugin portal)) return null;

        try {
            Method m = portal.getClass().getMethod("getBlastPowerupManager");
            Object o = m.invoke(portal);
            if (o instanceof BlastPowerupManager pm) return pm;
        } catch (Throwable ignored) {}

        try {
            Field f = portal.getClass().getDeclaredField("blastPowerupManager");
            f.setAccessible(true);
            Object o = f.get(portal);
            if (o instanceof BlastPowerupManager pm) return pm;
        } catch (Throwable ignored) {}

        return null;
    }

    public void syncBlastPowerupUi(Player p) {
        if (!blastPowerupUiEnabled()) return;
        if (p == null || !p.isOnline()) return;
        if (getGameState(p) != GameState.BLAST) return;

        BlastPowerupManager pm = resolvePowerupManager();
        if (pm == null) return;

        PlayerInventory inv = p.getInventory();
        if (inv == null) return;

        List<Integer> baseSlots = getUiBaseSlots();
        Set<Integer> reserved = getReservedUiSlots();

        for (int i = 0; i < UI_POWERUPS.size(); i++) {
            BlastPowerupType type = UI_POWERUPS.get(i);
            int baseSlot = baseSlots.get(i);
            int indSlot = baseSlot + 1;

            relocateIfNeeded(p, inv, baseSlot, reserved);
            relocateIfNeeded(p, inv, indSlot, reserved);

            int stacks = pm.getStacks(p, type);

            ItemStack powerup = createPowerupItem(type, stacks);
            inv.setItem(baseSlot, powerup);

            ItemStack indicator = createIndicatorItem(type, stacks);
            inv.setItem(indSlot, indicator);
        }

        // IMPORTANT: always enforce potion effects to match stacks when UI is synced
        try { pm.applyEffects(p); } catch (Throwable ignored) {}
    }

    public void clearBlastPowerupUi(Player p) {
        if (p == null) return;

        PlayerInventory inv = p.getInventory();
        if (inv == null) return;

        for (int slot : getReservedUiSlots()) {
            ItemStack it = inv.getItem(slot);
            if (isUiPowerupItem(it) || isUiIndicatorItem(it)) {
                inv.setItem(slot, null);
            }
        }
    }

    private void relocateIfNeeded(Player p, PlayerInventory inv, int slot, Set<Integer> reserved) {
        ItemStack existing = inv.getItem(slot);
        if (existing == null || existing.getType().isAir()) return;

        if (isUiPowerupItem(existing) || isUiIndicatorItem(existing)) return;

        int dest = findFirstEmptyNonReserved(inv, reserved);
        if (dest >= 0) {
            inv.setItem(dest, existing);
            inv.setItem(slot, null);
        } else {
            try { p.getWorld().dropItemNaturally(p.getLocation(), existing); } catch (Throwable ignored) {}
            inv.setItem(slot, null);
        }
    }

    private int findFirstEmptyNonReserved(PlayerInventory inv, Set<Integer> reserved) {
        for (int i = 0; i <= 35; i++) {
            if (reserved.contains(i)) continue;
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) return i;
        }
        return -1;
    }

    private boolean isUiPowerupItem(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        String v = it.getItemMeta().getPersistentDataContainer().get(blastPowerupUiKey, PersistentDataType.STRING);
        return v != null && !v.isBlank();
    }

    private boolean isUiIndicatorItem(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        String v = it.getItemMeta().getPersistentDataContainer().get(blastPowerupUiIndicatorKey, PersistentDataType.STRING);
        return v != null && !v.isBlank();
    }

    private BlastPowerupType getUiType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;

        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();

        String v = pdc.get(blastPowerupUiKey, PersistentDataType.STRING);
        if (v == null || v.isBlank()) v = pdc.get(blastPowerupUiIndicatorKey, PersistentDataType.STRING);
        if (v == null || v.isBlank()) return null;

        try {
            return BlastPowerupType.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack createPowerupItem(BlastPowerupType type, int stacks) {
        Material mat = switch (type) {
            case SPEED -> Material.SUGAR;
            case JUMP -> Material.RABBIT_FOOT;
            case BLAST_SPEED -> Material.CLOCK;
            case BLASTER_DAMAGE -> Material.ANVIL;
            case DASH -> Material.FEATHER;
            case KNOCKBACK -> Material.IRON_SWORD;
            case SLOW_SHOT -> Material.ICE;
            case BLIND_SHOT -> Material.INK_SAC;
            case MARK_TARGET -> Material.COMPASS;
            default -> Material.NETHER_STAR;
        };

        int clamped = Math.max(0, Math.min(UI_MAX_STACKS, stacks));

        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String nice = switch (type) {
                case SPEED -> "Speed";
                case JUMP -> "Jump";
                case BLAST_SPEED -> "Blast Speed";
                case BLASTER_DAMAGE -> "Blaster Damage";
                case DASH -> "Dash";
                case KNOCKBACK -> "Knockback";
                case SLOW_SHOT -> "Slow Shot";
                case BLIND_SHOT -> "Blind Shot";
                case MARK_TARGET -> "Mark Target";
                case CONFUSION -> "Confusion";
                default -> type.name();
            };

            meta.setDisplayName("§b" + nice + " §7(" + clamped + "/" + UI_MAX_STACKS + ")");
            meta.setLore(buildPowerupLore(type, clamped));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(blastPowerupUiKey, PersistentDataType.STRING, type.name());
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack createIndicatorItem(BlastPowerupType type, int stacks) {
        return null;
    }

    private List<String> buildPowerupLore(BlastPowerupType type, int stacks) {
        String stackLine = "§7Stacks: §b" + stacks + "/" + UI_MAX_STACKS;
        return switch (type) {
            case SPEED -> List.of(
                    "§7Gain Speed I/II/III based on stacks.",
                    "§7Chance: §b100% (always active).",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            case JUMP -> List.of(
                    "§7Gain Jump Boost II/III/IV.",
                    "§7Chance: §b100% (always active).",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            case BLAST_SPEED -> List.of(
                    "§7Reduce blaster cooldown by 0.2s per stack.",
                    "§7Chance: §b100% (always active).",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            case BLASTER_DAMAGE -> List.of(
                    "§7Remove extra armor pieces per hit.",
                    "§7+1/+2/+3 pieces at stacks 1/2/3.",
                    "§7Chance: §b100% on hit.",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            case DASH -> List.of(
                    "§7Left-click with blaster to dash.",
                    "§7Distance: §b6/8/10 §7blocks.",
                    "§7Chance: §b100% (5s cooldown).",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            case KNOCKBACK -> List.of(
                    "§7Stronger knockback on hit.",
                    "§7Strength: §b0.8/1.2/1.6§7.",
                    "§7Chance: §b100% on hit.",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            case SLOW_SHOT -> List.of(
                    "§7Slow targets hit by your blaster.",
                    "§7Duration: §b0.5/1/1s§7 (higher slow).",
                    "§7Chance: §b100% on hit.",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            case BLIND_SHOT -> List.of(
                    "§7Chance to blind targets on hit.",
                    "§7Duration: §b1/2/3s§7.",
                    "§7Chance: §b35% on hit.",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            case MARK_TARGET -> List.of(
                    "§7Outline targets on hit (glowing).",
                    "§7Duration: §b3/5/8s§7.",
                    "§7Chance: §b100% on hit.",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
            default -> List.of(
                    "§7Powerup details unavailable.",
                    stackLine,
                    "§7Cost: §b1 §7Diamond",
                    "§7Click to purchase"
            );
        };
    }

    private void attemptPurchase(Player p, BlastPowerupType type) {
        if (p == null || type == null) return;
        if (getGameState(p) != GameState.BLAST) return;

        BlastPowerupManager pm = resolvePowerupManager();
        if (pm == null) return;

        BlastPowerupManager.PurchaseResult res = pm.tryPurchase(p, type);

        if (res == BlastPowerupManager.PurchaseResult.SUCCESS) {
            p.sendMessage("§a[BLAST] Purchased " + type.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
        } else if (res == BlastPowerupManager.PurchaseResult.NO_DIAMOND) {
            p.sendMessage("§c[BLAST] You need a diamond to buy that powerup.");
        } else if (res == BlastPowerupManager.PurchaseResult.MAXED) {
            p.sendMessage("§e[BLAST] That powerup is already maxed.");
        }

        syncBlastPowerupUi(p);
        try { p.updateInventory(); } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onBlastPowerupUiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (getGameState(p) != GameState.BLAST) return;
        if (!blastPowerupUiEnabled()) return;

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        if (isUiPowerupItem(cursor) || isUiIndicatorItem(cursor)) {
            e.setCancelled(true);
            return;
        }

        if (isUiPowerupItem(current) || isUiIndicatorItem(current)) {
            e.setCancelled(true);
            BlastPowerupType type = getUiType(current);
            if (type != null) {
                Bukkit.getScheduler().runTask(plugin, () -> attemptPurchase(p, type));
            }
        }
    }

    @EventHandler
    public void onBlastPowerupUiDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (getGameState(p) != GameState.BLAST) return;
        if (!blastPowerupUiEnabled()) return;

        Set<Integer> reserved = getReservedUiSlots();
        int topSize = e.getView().getTopInventory().getSize();

        for (int raw : e.getRawSlots()) {
            int slot = raw - topSize;
            if (slot >= 0 && reserved.contains(slot)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBlastPowerupUiDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        if (getGameState(p) != GameState.BLAST) return;
        if (!blastPowerupUiEnabled()) return;

        ItemStack drop = e.getItemDrop() != null ? e.getItemDrop().getItemStack() : null;
        if (isUiPowerupItem(drop) || isUiIndicatorItem(drop)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlastPowerupUiSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        if (getGameState(p) != GameState.BLAST) return;
        if (!blastPowerupUiEnabled()) return;

        ItemStack main = e.getMainHandItem();
        ItemStack off = e.getOffHandItem();

        if (isUiPowerupItem(main) || isUiIndicatorItem(main) || isUiPowerupItem(off) || isUiIndicatorItem(off)) {
            e.setCancelled(true);
        }
    }
}
