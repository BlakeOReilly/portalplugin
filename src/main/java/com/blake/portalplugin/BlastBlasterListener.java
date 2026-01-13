package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class BlastBlasterListener implements Listener {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final BlastCooldownTracker cooldownTracker;

    private static final long BASIC_COOLDOWN_MS = 800;
    private static final long BIG_COOLDOWN_MS = 2500;

    private static final double MAX_RANGE = 32.0;

    // Hotbar index (9th slot) - previously used by diamond HUD logic
    private static final int DIAMOND_SLOT = 8;

    public BlastBlasterListener(PortalPlugin plugin, GameStateManager gsm, BlastCooldownTracker cooldownTracker) {
        this.plugin = plugin;
        this.gameStateManager = gsm;
        this.cooldownTracker = cooldownTracker;
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;

        // Only care about BLAST context for messages/other logic
        if (gameStateManager.getGameState(p) != GameState.BLAST) return;

        Item itemEnt = e.getItem();
        if (itemEnt == null) return;

        ItemStack stack = itemEnt.getItemStack();
        if (stack == null || stack.getType().isAir()) return;

        // IMPORTANT:
        // Do NOT cancel diamond pickup or force diamonds into a designated slot.
        // Let Minecraft handle diamonds normally.

        // Existing Big Blaster pickup message (unchanged)
        if (BlastItems.isBigBlaster(plugin, stack)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) p.sendMessage("§6[BLAST] You picked up the Big Blaster!");
            });
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player shooter = e.getPlayer();
        if (shooter == null) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        boolean isBasic = BlastItems.isBasicBlaster(plugin, item);
        boolean isBig = BlastItems.isBigBlaster(plugin, item);

        if (!isBasic && !isBig) return;

        if (gameStateManager.getGameState(shooter) != GameState.BLAST) return;

        e.setCancelled(true);

        if (isBasic) {
            if (!canFireBasic(shooter)) return;
            shootBasicInstant(shooter);
        } else {
            if (!canFireBig(shooter)) return;
            shootBigFast(shooter);
        }
    }

    private boolean canFireBasic(Player p) {
        long now = System.currentTimeMillis();

        long cd = adjustedCooldownMs(p, BASIC_COOLDOWN_MS);
        if (cooldownTracker != null
                && !cooldownTracker.isReady(p.getUniqueId(), BlastCooldownTracker.CooldownType.BASIC, now)) {
            return false;
        }

        if (cooldownTracker != null) {
            cooldownTracker.startCooldown(p.getUniqueId(), BlastCooldownTracker.CooldownType.BASIC, cd, now);
        }
        return true;
    }

    private boolean canFireBig(Player p) {
        long now = System.currentTimeMillis();

        long cd = adjustedCooldownMs(p, BIG_COOLDOWN_MS);
        if (cooldownTracker != null
                && !cooldownTracker.isReady(p.getUniqueId(), BlastCooldownTracker.CooldownType.BIG, now)) {
            return false;
        }

        if (cooldownTracker != null) {
            cooldownTracker.startCooldown(p.getUniqueId(), BlastCooldownTracker.CooldownType.BIG, cd, now);
        }
        return true;
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

    private void shootBasicInstant(Player shooter) {
        Location start = shooter.getEyeLocation().clone();
        World world = start.getWorld();
        if (world == null) return;

        world.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.25f);

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        Vector dir = start.getDirection().normalize();

        RayTraceResult entRes = null;
        RayTraceResult blkRes = null;

        try {
            entRes = world.rayTraceEntities(start, dir, MAX_RANGE, 0.5, ent -> {
                if (!(ent instanceof Player p)) return false;
                if (p.getUniqueId().equals(shooter.getUniqueId())) return false;
                return gameStateManager.getGameState(p) == GameState.BLAST;
            });
        } catch (Throwable ignored) {}

        try {
            blkRes = world.rayTraceBlocks(start, dir, MAX_RANGE);
        } catch (Throwable ignored) {}

        double endDist = MAX_RANGE;
        Player hitPlayer = null;
        Block hitBlock = null;
        Location impactLoc;

        double entDist = Double.MAX_VALUE;
        if (entRes != null && entRes.getHitPosition() != null && entRes.getHitEntity() instanceof Player p) {
            entDist = entRes.getHitPosition().distance(start.toVector());
            hitPlayer = p;
        }

        double blkDist = Double.MAX_VALUE;
        if (blkRes != null && blkRes.getHitPosition() != null && blkRes.getHitBlock() != null) {
            blkDist = blkRes.getHitPosition().distance(start.toVector());
            hitBlock = blkRes.getHitBlock();
        }

        if (entDist < blkDist) {
            endDist = Math.min(MAX_RANGE, entDist);
            impactLoc = vecToLoc(world, entRes.getHitPosition());
        } else if (blkDist < Double.MAX_VALUE) {
            endDist = Math.min(MAX_RANGE, blkDist);
            impactLoc = vecToLoc(world, blkRes.getHitPosition());
        } else {
            impactLoc = start.clone().add(dir.clone().multiply(MAX_RANGE));
        }

        for (double d = 0.0; d <= endDist; d += 0.45) {
            Location point = start.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
        }

        if (hitPlayer != null && entDist < blkDist) {
            if (bm != null) bm.applyBasicHit(shooter, hitPlayer, impactLoc);
            return;
        }

        if (hitBlock != null && blkDist < Double.MAX_VALUE) {
            if (BlastItems.isColoredWool(hitBlock.getType())) {
                hitBlock.setType(Material.AIR, false);
            }
        }
    }

    private void shootBigFast(Player shooter) {
        Location start = shooter.getEyeLocation().clone();
        World world = start.getWorld();
        if (world == null) return;

        world.playSound(start, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.9f);

        BlastMinigameManager bm = plugin.getBlastMinigameManager();

        final double totalTicksToMax = 12.0;
        final double step = MAX_RANGE / totalTicksToMax;

        new BukkitRunnable() {
            double dist = 0.0;

            @Override
            public void run() {
                if (!shooter.isOnline()) { cancel(); return; }
                if (gameStateManager.getGameState(shooter) != GameState.BLAST) { cancel(); return; }
                if (dist >= MAX_RANGE) { cancel(); return; }

                double prev = dist;
                dist = Math.min(MAX_RANGE, dist + step);

                Vector dir = start.getDirection().normalize();

                for (double d = prev; d <= dist; d += 0.35) {
                    Location point = start.clone().add(dir.clone().multiply(d));

                    double mod = (d % 4.0);
                    if (mod < 3.0) {
                        world.spawnParticle(Particle.FIREWORK, point, 2, 0.02, 0.02, 0.02, 0.01);
                    }

                    Player hitPlayer = findHitPlayer(shooter, point, 0.60);
                    if (hitPlayer != null) {
                        if (bm != null) {
                            bm.applyBigDirectHit(shooter, hitPlayer, point);

                            playBigImpactEffect(point);

                            Set<UUID> processed = new HashSet<>();
                            processed.add(hitPlayer.getUniqueId());
                            bm.applyBigAoE(shooter, point, 5.0, processed);

                            break3x3Wool(point.getBlock());
                        }
                        cancel();
                        return;
                    }

                    Block b = point.getBlock();
                    if (b != null && !b.getType().isAir() && !b.isPassable()) {
                        playBigImpactEffect(point);

                        if (bm != null) {
                            bm.applyBigAoE(shooter, point, 5.0, new HashSet<>());
                        }

                        break3x3Wool(b);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Player findHitPlayer(Player shooter, Location point, double radius) {
        if (point == null || point.getWorld() == null) return null;

        Collection<Entity> nearby = point.getWorld().getNearbyEntities(point, radius, radius, radius, ent -> ent instanceof Player);
        Player best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity ent : nearby) {
            if (!(ent instanceof Player p)) continue;
            if (p.getUniqueId().equals(shooter.getUniqueId())) continue;
            if (!p.isOnline()) continue;
            if (gameStateManager.getGameState(p) != GameState.BLAST) continue;

            double d = p.getEyeLocation().distanceSquared(point);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private void playBigImpactEffect(Location center) {
        if (center == null || center.getWorld() == null) return;
        World w = center.getWorld();

        try {
            Firework fw = w.spawn(center, Firework.class);
            FireworkMeta fm = fw.getFireworkMeta();
            fm.setPower(2);
            fm.addEffect(org.bukkit.FireworkEffect.builder()
                    .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                    .flicker(true)
                    .trail(true)
                    .withColor(org.bukkit.Color.ORANGE, org.bukkit.Color.RED, org.bukkit.Color.YELLOW)
                    .build());
            fw.setFireworkMeta(fm);

            Bukkit.getScheduler().runTask(plugin, fw::detonate);
        } catch (Throwable ignored) {}

        w.spawnParticle(Particle.EXPLOSION, center, 10, 2.5, 2.5, 2.5, 0.02);
        w.spawnParticle(Particle.FLAME, center, 120, 2.5, 1.5, 2.5, 0.02);
        w.spawnParticle(Particle.SMOKE, center, 60, 2.5, 1.5, 2.5, 0.02);
    }

    private void break3x3Wool(Block centerBlock) {
        if (centerBlock == null) return;

        int y = centerBlock.getY();
        World w = centerBlock.getWorld();
        int cx = centerBlock.getX();
        int cz = centerBlock.getZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = w.getBlockAt(cx + dx, y, cz + dz);
                if (b == null) continue;

                if (BlastItems.isColoredWool(b.getType())) {
                    b.setType(Material.AIR, false);
                }
            }
        }
    }

    private Location vecToLoc(World w, Vector v) {
        return new Location(w, v.getX(), v.getY(), v.getZ());
    }

    private void tryInvokeBoolean(Object target, String methodName, boolean arg) {
        if (target == null || methodName == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, boolean.class);
            m.invoke(target, arg);
        } catch (Throwable ignored) {}
    }
}
