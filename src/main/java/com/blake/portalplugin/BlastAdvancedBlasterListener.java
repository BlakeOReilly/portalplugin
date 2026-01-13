package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class BlastAdvancedBlasterListener implements Listener {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final BlastCooldownTracker cooldownTracker;

    // Active strike charges
    private final Map<UUID, StrikeCharge> strikeCharges = new HashMap<>();

    private static final long SCATTER_COOLDOWN_MS = 3000;
    private static final long RANGE_COOLDOWN_MS = 3000;
    private static final long STRIKE_COOLDOWN_MS = 10000;

    private static final double SCATTER_RANGE = 20.0;
    private static final double SCATTER_HALF_ANGLE_RAD = Math.toRadians(22.5); // 45° cone total
    private static final double RANGE_MAX = 128.0;

    private static final int STRIKE_CHARGE_TICKS = 12 * 20;

    private static final String STRIKE_GUI_TITLE = "§bStrike Target";
    private final NamespacedKey strikeGuiTeamKey;

    public BlastAdvancedBlasterListener(PortalPlugin plugin, GameStateManager gsm, BlastCooldownTracker cooldownTracker) {
        this.plugin = plugin;
        this.gameStateManager = gsm;
        this.cooldownTracker = cooldownTracker;
        this.strikeGuiTeamKey = new NamespacedKey(plugin, "strike_gui_team");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player shooter = e.getPlayer();
        if (shooter == null) return;

        if (gameStateManager.getGameState(shooter) != GameState.BLAST) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        if (strikeCharges.containsKey(shooter.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        boolean isScatter = BlastItems.isScatterBlaster(plugin, item);
        boolean isRange = BlastItems.isRangeBlaster(plugin, item);
        boolean isStrike = BlastItems.isStrikeBlaster(plugin, item);

        if (!isScatter && !isRange && !isStrike) return;

        e.setCancelled(true);

        if (isScatter) {
            long now = System.currentTimeMillis();
            long cd = adjustedCooldownMs(shooter, SCATTER_COOLDOWN_MS);
            if (cooldownTracker != null
                    && !cooldownTracker.isReady(shooter.getUniqueId(), BlastCooldownTracker.CooldownType.SCATTER, now)) {
                return;
            }
            if (cooldownTracker != null) {
                cooldownTracker.startCooldown(shooter.getUniqueId(), BlastCooldownTracker.CooldownType.SCATTER, cd, now);
            }
            fireScatter(shooter);
            return;
        }

        if (isRange) {
            if (!ensureLimitedUseAvailable(shooter, item)) return;

            long now = System.currentTimeMillis();
            long cd = adjustedCooldownMs(shooter, RANGE_COOLDOWN_MS);
            if (cooldownTracker != null
                    && !cooldownTracker.isReady(shooter.getUniqueId(), BlastCooldownTracker.CooldownType.RANGE, now)) {
                return;
            }
            if (cooldownTracker != null) {
                cooldownTracker.startCooldown(shooter.getUniqueId(), BlastCooldownTracker.CooldownType.RANGE, cd, now);
            }
            fireRange(shooter);
            consumeLimitedUse(shooter, item);
            return;
        }

        if (isStrike) {
            if (!ensureLimitedUseAvailable(shooter, item)) return;

            long now = System.currentTimeMillis();
            if (cooldownTracker != null
                    && !cooldownTracker.isReady(shooter.getUniqueId(), BlastCooldownTracker.CooldownType.STRIKE, now)) {
                return;
            }

            openStrikeGui(shooter);
        }
    }

    /**
     * Applies BLAST_SPEED powerup (0.2s / 200ms per stack) to any cooldown.
     * Uses reflection so this compiles even if PortalPlugin getter name differs.
     */
    private long adjustedCooldownMs(Player p, long baseMs) {
        BlastPowerupManager pm = resolvePowerups();
        if (pm == null) return baseMs;

        try {
            return pm.adjustBlasterCooldownMs(p, baseMs);
        } catch (Throwable ignored) {
            return baseMs;
        }
    }

    private BlastPowerupManager resolvePowerups() {
        if (plugin == null) return null;

        // Preferred: public getter
        try {
            Method m = plugin.getClass().getMethod("getBlastPowerupManager");
            Object o = m.invoke(plugin);
            if (o instanceof BlastPowerupManager bpm) return bpm;
        } catch (Throwable ignored) {}

        // Fallback: field access
        try {
            Field f = plugin.getClass().getDeclaredField("blastPowerupManager");
            f.setAccessible(true);
            Object o = f.get(plugin);
            if (o instanceof BlastPowerupManager bpm) return bpm;
        } catch (Throwable ignored) {}

        return null;
    }

    private void fireScatter(Player shooter) {
        Location eye = shooter.getEyeLocation().clone();
        World world = eye.getWorld();
        if (world == null) return;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;

        playSoundSafe(world, shooter.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.35f);
        playSoundSafe(world, shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.95f);

        Vector baseDir = eye.getDirection().normalize();
        float baseYaw = eye.getYaw();
        float basePitch = eye.getPitch();

        Random rng = java.util.concurrent.ThreadLocalRandom.current();
        int rays = 45;

        int maxBreaks = 24;
        int breaks = 0;

        for (int i = 0; i < rays; i++) {
            float yawOff = (float) (rng.nextDouble(-22.5, 22.5));
            float pitchOff = (float) (rng.nextDouble(-14.0, 14.0));

            Vector dir = directionFromYawPitch(eye, baseYaw + yawOff, basePitch + pitchOff).normalize();

            for (double d = 0.7; d <= SCATTER_RANGE; d += 0.85) {
                Location pt = eye.clone().add(dir.clone().multiply(d));
                world.spawnParticle(Particle.CRIT, pt, 2, 0.04, 0.04, 0.04, 0.01);
                world.spawnParticle(Particle.SMOKE, pt, 1, 0.04, 0.04, 0.04, 0.0);
            }

            if (breaks < maxBreaks) {
                try {
                    RayTraceResult br = world.rayTraceBlocks(eye, dir, SCATTER_RANGE);
                    if (br != null && br.getHitBlock() != null) {
                        Material m = br.getHitBlock().getType();
                        if (BlastItems.isColoredWool(m)) {
                            br.getHitBlock().setType(Material.AIR, false);
                            breaks++;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        double cosHalf = Math.cos(SCATTER_HALF_ANGLE_RAD);

        Collection<Entity> nearby = world.getNearbyEntities(
                shooter.getLocation(),
                SCATTER_RANGE, SCATTER_RANGE, SCATTER_RANGE,
                ent -> ent instanceof Player
        );

        List<Player> victims = new ArrayList<>();
        for (Entity ent : nearby) {
            if (!(ent instanceof Player v)) continue;
            if (!v.isOnline()) continue;
            if (v.getUniqueId().equals(shooter.getUniqueId())) continue;
            if (gameStateManager.getGameState(v) != GameState.BLAST) continue;
            if (bm.getTeam(v) == null) continue;

            Vector to = v.getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist <= 0.1 || dist > SCATTER_RANGE) continue;

            Vector toN = to.clone().multiply(1.0 / dist);
            double cos = baseDir.dot(toN);
            if (cos >= cosHalf) {
                victims.add(v);
            }
        }

        for (Player v : victims) {
            bm.applyInstantElim(shooter, v, BlastDamageSource.DEFAULT, "Scatter Blaster");
        }
    }

    private void fireRange(Player shooter) {
        Location start = shooter.getEyeLocation().clone();
        World world = start.getWorld();
        if (world == null) return;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;

        playSoundSafe(world, shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.1f);

        Vector dir = start.getDirection().normalize();

        RayTraceResult entRes = null;
        RayTraceResult blkRes = null;

        try {
            entRes = world.rayTraceEntities(start, dir, RANGE_MAX, 0.55, ent -> {
                if (!(ent instanceof Player p)) return false;
                if (p.getUniqueId().equals(shooter.getUniqueId())) return false;
                if (gameStateManager.getGameState(p) != GameState.BLAST) return false;
                return bm.getTeam(p) != null;
            });
        } catch (Throwable ignored) {}

        try {
            blkRes = world.rayTraceBlocks(start, dir, RANGE_MAX);
        } catch (Throwable ignored) {}

        double entDist = Double.MAX_VALUE;
        Player hitPlayer = null;

        if (entRes != null && entRes.getHitPosition() != null && entRes.getHitEntity() instanceof Player p) {
            entDist = entRes.getHitPosition().distance(start.toVector());
            hitPlayer = p;
        }

        double blkDist = Double.MAX_VALUE;
        if (blkRes != null && blkRes.getHitPosition() != null && blkRes.getHitBlock() != null) {
            blkDist = blkRes.getHitPosition().distance(start.toVector());
        }

        double endDist;
        if (entDist < blkDist) endDist = Math.min(RANGE_MAX, entDist);
        else if (blkDist < Double.MAX_VALUE) endDist = Math.min(RANGE_MAX, blkDist);
        else endDist = RANGE_MAX;

        for (double d = 0.0; d <= endDist; d += 0.75) {
            Location pt = start.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);
        }

        if (hitPlayer != null && entDist < blkDist) {
            playSoundSafe(world, hitPlayer.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.7f, 1.25f);
            bm.applyInstantElim(shooter, hitPlayer, BlastDamageSource.DEFAULT, "Range Blaster");
        }
    }

    private void openStrikeGui(Player shooter) {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;

        BlastTeam myTeam = bm.getTeam(shooter);

        Inventory inv = Bukkit.createInventory(null, 9, STRIKE_GUI_TITLE);

        int slot = 2;
        for (BlastTeam t : BlastTeam.values()) {
            if (myTeam != null && t == myTeam) continue;
            ItemStack it = new ItemStack(t.getWoolMaterial(), 1);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(t.getColor() + t.getKey().toUpperCase());
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

                meta.getPersistentDataContainer().set(
                        strikeGuiTeamKey,
                        PersistentDataType.STRING,
                        t.getKey()
                );

                it.setItemMeta(meta);
            }
            inv.setItem(slot, it);
            slot += 2;
            if (slot >= 9) break;
        }

        shooter.openInventory(inv);
        playSoundSafe(shooter.getWorld(), shooter.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.6f);
    }

    @EventHandler
    public void onStrikeGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView() != null ? e.getView().getTitle() : "";
        if (!STRIKE_GUI_TITLE.equals(title)) return;

        e.setCancelled(true);

        if (gameStateManager.getGameState(p) != GameState.BLAST) {
            p.closeInventory();
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        String teamKey = clicked.getItemMeta().getPersistentDataContainer().get(
                strikeGuiTeamKey,
                PersistentDataType.STRING
        );
        BlastTeam chosen = BlastTeam.fromKey(teamKey);
        if (chosen == null) return;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) { p.closeInventory(); return; }

        BlastTeam myTeam = bm.getTeam(p);
        if (myTeam != null && chosen == myTeam) return;

        p.closeInventory();

        if (strikeCharges.containsKey(p.getUniqueId())) return;

        startStrikeCharge(p, chosen);
    }

    @EventHandler
    public void onStrikeGuiClose(InventoryCloseEvent e) {
        // no-op
    }

    private void startStrikeCharge(Player caster, BlastTeam target) {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;

        BlastStrikeUtil.markCharging(plugin, caster);

        Location lock = caster.getLocation().clone();
        UUID id = caster.getUniqueId();

        StrikeCharge charge = new StrikeCharge(lock, target);
        strikeCharges.put(id, charge);

        playSoundSafe(caster.getWorld(), caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);

        BukkitRunnable r = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!caster.isOnline()) { cancelStrikeInternal(id, false); return; }
                if (gameStateManager.getGameState(caster) != GameState.BLAST) { cancelStrikeInternal(id, false); return; }

                BlastMinigameManager bmNow = plugin.getBlastMinigameManager();
                if (bmNow == null || !bmNow.isInProgress()) { cancelStrikeInternal(id, false); return; }

                if (BlastStrikeUtil.consumeCancelRequest(plugin, caster)) {
                    caster.sendMessage("§c[BLAST] Strike charge cancelled (you were hit).");
                    cancelStrikeInternal(id, true);
                    return;
                }

                double progress = Math.min(1.0, tick / (double) STRIKE_CHARGE_TICKS);

                int secsLeft = (int) Math.ceil((STRIKE_CHARGE_TICKS - tick) / 20.0);
                sendActionBarSafe(caster, "§bStrike Charging: §f" + secsLeft + "s");

                spawnChargeSwirl(lock, caster, progress);

                tick++;
                if (tick > STRIKE_CHARGE_TICKS) {
                    finishStrikeCharge(caster, target, lock);
                    cancelStrikeInternal(id, true);
                }
            }
        };

        charge.task = r;
        r.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishStrikeCharge(Player caster, BlastTeam target, Location lock) {
        if (caster == null || !caster.isOnline()) return;

        BlastStrikeUtil.clearCharging(plugin, caster);

        World w = caster.getWorld();

        Location base = caster.getLocation().clone().add(0, 1.0, 0);
        for (double y = 0.0; y <= 26.0; y += 0.7) {
            w.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0, 0, 0, 0);
        }
        playSoundSafe(w, caster.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.6f);
        playSoundSafe(w, caster.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.0f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> strikeTeamFromSky(caster, target), 2L);

        long cd = adjustedCooldownMs(caster, STRIKE_COOLDOWN_MS);
        if (cooldownTracker != null) {
            cooldownTracker.startCooldown(caster.getUniqueId(), BlastCooldownTracker.CooldownType.STRIKE, cd);
        }
        consumeLimitedUse(caster, null);
    }

    private void strikeTeamFromSky(Player caster, BlastTeam target) {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;
        if (target == null) return;

        List<Player> targets = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            if (gameStateManager.getGameState(p) != GameState.BLAST) continue;

            BlastTeam t = bm.getTeam(p);
            if (t != target) continue;

            if (caster != null && caster.getUniqueId().equals(p.getUniqueId())) continue;

            targets.add(p);
        }

        for (Player v : targets) {
            Location loc = v.getLocation().clone();

            World w = loc.getWorld();
            if (w != null) {
                Location top = loc.clone().add(0, 24, 0);
                for (double d = 0; d <= 24; d += 0.8) {
                    w.spawnParticle(Particle.END_ROD, top.clone().add(0, -d, 0), 2, 0.02, 0.02, 0.02, 0.0);
                }
                try {
                    w.strikeLightningEffect(loc);
                } catch (Throwable ignored) {}
                playSoundSafe(w, loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 1.2f);
            }

            bm.applyInstantElim(caster, v, BlastDamageSource.STRIKE_BLASTER, "Strike Blaster");
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        if (!strikeCharges.containsKey(id)) return;

        BlastStrikeUtil.requestCancel(plugin, p);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        StrikeCharge ch = strikeCharges.get(p.getUniqueId());
        if (ch == null) return;

        Location to = e.getTo();
        if (to == null) return;

        Location lock = ch.lock;
        if (lock == null || lock.getWorld() == null) return;

        Location locked = new Location(
                lock.getWorld(),
                lock.getX(), lock.getY(), lock.getZ(),
                to.getYaw(), to.getPitch()
        );

        if (to.getWorld() != lock.getWorld()
                || to.distanceSquared(lock) > 0.0004) {
            e.setTo(locked);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        UUID id = p.getUniqueId();

        if (strikeCharges.containsKey(id)) {
            cancelStrikeInternal(id, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        cancelStrikeInternal(p.getUniqueId(), false);
    }

    private boolean ensureLimitedUseAvailable(Player shooter, ItemStack item) {
        if (shooter == null || item == null) return false;
        if (!BlastItems.isRangeBlaster(plugin, item) && !BlastItems.isStrikeBlaster(plugin, item)) return true;

        int usesLeft = BlastItems.getLimitedUsesLeft(plugin, item);
        if (usesLeft > 0) return true;

        removeItemFromHand(shooter, item);
        return false;
    }

    private void consumeLimitedUse(Player shooter, ItemStack item) {
        if (shooter == null) return;

        if (item != null && (BlastItems.isRangeBlaster(plugin, item) || BlastItems.isStrikeBlaster(plugin, item))) {
            int left = BlastItems.consumeLimitedUse(plugin, item);
            if (left <= 0) {
                removeItemFromHand(shooter, item);
            }
            return;
        }

        ItemStack main = shooter.getInventory().getItemInMainHand();
        if (BlastItems.isStrikeBlaster(plugin, main)) {
            int left = BlastItems.consumeLimitedUse(plugin, main);
            if (left <= 0) shooter.getInventory().setItemInMainHand(null);
            return;
        }

        ItemStack off = shooter.getInventory().getItemInOffHand();
        if (BlastItems.isStrikeBlaster(plugin, off)) {
            int left = BlastItems.consumeLimitedUse(plugin, off);
            if (left <= 0) shooter.getInventory().setItemInOffHand(null);
            return;
        }

        ItemStack[] contents = shooter.getInventory().getStorageContents();
        if (contents == null) return;

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!BlastItems.isStrikeBlaster(plugin, stack)) continue;
            int left = BlastItems.consumeLimitedUse(plugin, stack);
            if (left <= 0) contents[i] = null;
            shooter.getInventory().setStorageContents(contents);
            return;
        }
    }

    private void removeItemFromHand(Player shooter, ItemStack item) {
        if (shooter == null || item == null) return;
        ItemStack main = shooter.getInventory().getItemInMainHand();
        if (item.isSimilar(main)) {
            shooter.getInventory().setItemInMainHand(null);
            return;
        }
        ItemStack off = shooter.getInventory().getItemInOffHand();
        if (item.isSimilar(off)) {
            shooter.getInventory().setItemInOffHand(null);
        }
    }

    private void cancelStrikeInternal(UUID casterId, boolean applyCooldown) {
        StrikeCharge ch = strikeCharges.remove(casterId);
        Player p = Bukkit.getPlayer(casterId);

        if (ch != null && ch.task != null) {
            try { ch.task.cancel(); } catch (Throwable ignored) {}
        }

        if (p != null) {
            BlastStrikeUtil.clearCharging(plugin, p);
            sendActionBarSafe(p, "");
            if (applyCooldown) {
                long cd = adjustedCooldownMs(p, STRIKE_COOLDOWN_MS);
                if (cooldownTracker != null) {
                    cooldownTracker.startCooldown(casterId, BlastCooldownTracker.CooldownType.STRIKE, cd);
                }
            }
        }
    }

    private void spawnChargeSwirl(Location lock, Player caster, double progress) {
        if (caster == null || !caster.isOnline()) return;
        World w = caster.getWorld();
        if (w == null) return;

        double baseY = lock.getY() - 0.3;
        double topY = lock.getY() + 2.1;
        double y = baseY + (topY - baseY) * progress;

        double angle = (System.currentTimeMillis() / 35.0) % (Math.PI * 2);
        double radius = 0.75;

        double x1 = Math.cos(angle) * radius;
        double z1 = Math.sin(angle) * radius;
        double x2 = Math.cos(angle + Math.PI) * radius;
        double z2 = Math.sin(angle + Math.PI) * radius;

        Location p1 = new Location(w, lock.getX() + 0.5 + x1, y, lock.getZ() + 0.5 + z1);
        Location p2 = new Location(w, lock.getX() + 0.5 + x2, y, lock.getZ() + 0.5 + z2);

        w.spawnParticle(Particle.WITCH, p1, 2, 0.02, 0.02, 0.02, 0.0);
        w.spawnParticle(Particle.WITCH, p2, 2, 0.02, 0.02, 0.02, 0.0);

        Location center = caster.getLocation().clone().add(0, 1.0, 0);
        w.spawnParticle(Particle.END_ROD, center, 1, 0.25, 0.25, 0.25, 0.0);
    }

    private Vector directionFromYawPitch(Location basis, float yaw, float pitch) {
        Location l = basis.clone();
        l.setYaw(yaw);
        l.setPitch(pitch);
        return l.getDirection();
    }

    private void playSoundSafe(World w, Location at, Sound sound, float vol, float pitch) {
        if (w == null || at == null || sound == null) return;
        try {
            w.playSound(at, sound, vol, pitch);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sendActionBarSafe(Player p, String msg) {
        if (p == null || !p.isOnline()) return;

        try {
            Method m = p.getClass().getMethod("sendActionBar", String.class);
            m.invoke(p, msg == null ? "" : msg);
            return;
        } catch (Throwable ignored) {}

        try {
            Object spigot = p.getClass().getMethod("spigot").invoke(p);

            Class<?> chatMsgType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBarEnum = Enum.valueOf((Class<? extends Enum>) chatMsgType, "ACTION_BAR");

            Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Method fromLegacy = textComponent.getMethod("fromLegacyText", String.class);
            Object baseComponents = fromLegacy.invoke(null, msg == null ? "" : msg);

            Class<?> baseComponentArray = Class.forName("[Lnet.md_5.bungee.api.chat.BaseComponent;");
            Method sendMsg = spigot.getClass().getMethod("sendMessage", chatMsgType, baseComponentArray);

            sendMsg.invoke(spigot, actionBarEnum, baseComponents);
            return;
        } catch (Throwable ignored) {}

        if (msg != null && !msg.isBlank()) {
            p.sendMessage(msg);
        }
    }

    private static final class StrikeCharge {
        final Location lock;
        final BlastTeam target;
        BukkitRunnable task;

        StrikeCharge(Location lock, BlastTeam target) {
            this.lock = lock;
            this.target = target;
        }
    }
}
