package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public class BlastUtilityItemsListener implements Listener {

    private final PortalPlugin plugin;
    private final GameStateManager gsm;

    public BlastUtilityItemsListener(PortalPlugin plugin, GameStateManager gsm) {
        this.plugin = plugin;
        this.gsm = gsm;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (p == null) return;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;
        if (gsm.getGameState(p) != GameState.BLAST || !bm.isParticipant(p)) return;

        ItemStack it = e.getItem();
        if (it == null || it.getType().isAir()) return;

        String id = BlastShopItems.getShopId(plugin, it);
        if (id == null) return;

        switch (id) {
            case "INSTANT_WALL" -> {
                e.setCancelled(true);
                if (consumeOneFromHand(p)) {
                    placeInstantWall(p, bm);
                }
            }
            case "TEAM_LIFE" -> {
                e.setCancelled(true);
                BlastTeam team = bm.getTeam(p);
                if (team == null) return;
                if (consumeOneFromHand(p)) {
                    bm.addTeamLives(team, 1);
                    p.sendMessage("§a[BLAST] +1 Team Life purchased for " + team.getColor() + team.getKey().toUpperCase() + "§a.");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                }
            }
            case "TRACKER" -> {
                e.setCancelled(true);
                trackNearestEnemy(p, bm);
            }
            case "HOMING" -> {
                e.setCancelled(true);
                if (consumeOneFromHand(p)) {
                    fireHomingMissile(p, bm);
                }
            }
            case "FIREBALL" -> {
                e.setCancelled(true);
                if (consumeOneFromHand(p)) {
                    launchFireball(p);
                }
            }
        }
    }

    private boolean consumeOneFromHand(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main == null || main.getType().isAir()) return false;

        int amt = main.getAmount();
        if (amt <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            main.setAmount(amt - 1);
            p.getInventory().setItemInMainHand(main);
        }
        p.updateInventory();
        return true;
    }

    private void placeInstantWall(Player p, BlastMinigameManager bm) {
        BlastTeam team = bm.getTeam(p);
        Material wool = (team != null) ? BlastItems.getTeamWool(team) : Material.WHITE_WOOL;

        Location base = p.getLocation().getBlock().getLocation();
        Vector dir = p.getLocation().getDirection().setY(0).normalize();

        // Block in front of player
        Location front = base.clone().add(dir.clone().multiply(1.0));

        // Determine perpendicular axis for width
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        int width = 3; // 3 wide
        int height = 2; // 2 high

        int placed = 0;

        for (int w = -(width / 2); w <= (width / 2); w++) {
            for (int h = 0; h < height; h++) {
                Location at = front.clone().add(right.clone().multiply(w)).add(0, h, 0);
                var block = at.getBlock();

                if (block.getType().isAir()) {
                    block.setType(wool, false);
                    placed++;
                }
            }
        }

        p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.8f, 1.1f);
        p.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 10, 0.4, 0.2, 0.4, 0.01);

        if (placed == 0) {
            p.sendMessage("§c[BLAST] No space to place the wall.");
        }
    }

    private void trackNearestEnemy(Player p, BlastMinigameManager bm) {
        Player target = findNearestEnemy(p, bm, 200.0);
        if (target == null) {
            p.sendMessage("§c[BLAST] No enemy found to track.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            return;
        }

        p.setCompassTarget(target.getLocation());
        p.sendMessage("§e[BLAST] Tracking: §f" + target.getName());
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
    }

    private void fireHomingMissile(Player shooter, BlastMinigameManager bm) {
        Player target = findNearestEnemy(shooter, bm, 200.0);
        if (target == null) {
            shooter.sendMessage("§c[BLAST] No enemy found for homing missile.");
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            return;
        }

        Snowball sb = shooter.launchProjectile(Snowball.class);
        sb.setShooter(shooter);

        // Mark it
        try {
            sb.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "blast_homing"), org.bukkit.persistence.PersistentDataType.STRING, target.getUniqueId().toString());
        } catch (Throwable ignored) {}

        shooter.playSound(shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.1f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > 80) { // ~4 seconds
                    try { sb.remove(); } catch (Throwable ignored) {}
                    cancel();
                    return;
                }

                if (sb.isDead() || !sb.isValid()) {
                    cancel();
                    return;
                }

                if (!target.isOnline() || gsm.getGameState(target) != GameState.BLAST) {
                    cancel();
                    return;
                }

                Vector to = target.getEyeLocation().toVector().subtract(sb.getLocation().toVector());
                double dist = to.length();
                if (dist < 0.2) dist = 0.2;

                Vector desired = to.normalize().multiply(0.65);
                Vector cur = sb.getVelocity();

                // Smooth steering
                Vector next = cur.multiply(0.65).add(desired.multiply(0.35));
                sb.setVelocity(next);

                sb.getWorld().spawnParticle(Particle.END_ROD, sb.getLocation(), 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void launchFireball(Player shooter) {
        Fireball fb = shooter.launchProjectile(Fireball.class);
        fb.setShooter(shooter);

        try { fb.setYield(0f); } catch (Throwable ignored) {}
        try { fb.setIsIncendiary(false); } catch (Throwable ignored) {}

        try {
            fb.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "blast_fireball"), org.bukkit.persistence.PersistentDataType.INTEGER, 1);
        } catch (Throwable ignored) {}

        shooter.playSound(shooter.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (proj == null) return;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;

        // Fireball impact
        if (proj instanceof Fireball fb) {
            Integer mark = null;
            try {
                mark = fb.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "blast_fireball"), org.bukkit.persistence.PersistentDataType.INTEGER);
            } catch (Throwable ignored) {}

            if (mark == null || mark != 1) return;

            Player shooter = (fb.getShooter() instanceof Player p) ? p : null;

            Location impact = fb.getLocation();
            impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 2, 0.2, 0.2, 0.2, 0);
            impact.getWorld().spawnParticle(Particle.FLAME, impact, 40, 0.8, 0.4, 0.8, 0.02);
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);

            // Small AoE elim
            bm.applyBigAoE(shooter, impact, 3.5, null);

            try { fb.remove(); } catch (Throwable ignored) {}
            return;
        }

        // Homing missile hit (snowball)
        if (proj instanceof Snowball sb) {
            String targetId = null;
            try {
                targetId = sb.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "blast_homing"), org.bukkit.persistence.PersistentDataType.STRING);
            } catch (Throwable ignored) {}

            if (targetId == null) return;

            Player shooter = (sb.getShooter() instanceof Player p) ? p : null;

            // If hit entity is player, elim directly
            if (e.getHitEntity() instanceof Player victim) {
                bm.applyInstantElim(shooter, victim);
            } else {
                // Small AoE on block impact
                bm.applyBigAoE(shooter, sb.getLocation(), 2.5, null);
            }

            sb.getWorld().spawnParticle(Particle.EXPLOSION, sb.getLocation(), 1, 0.2, 0.2, 0.2, 0);
            sb.getWorld().playSound(sb.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);

            try { sb.remove(); } catch (Throwable ignored) {}
        }
    }

    private Player findNearestEnemy(Player p, BlastMinigameManager bm, double maxDist) {
        BlastTeam my = bm.getTeam(p);
        if (my == null) return null;

        double best = maxDist * maxDist;
        Player bestP = null;

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == null || !other.isOnline()) continue;
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            if (gsm.getGameState(other) != GameState.BLAST) continue;
            if (!bm.isParticipant(other)) continue;

            BlastTeam ot = bm.getTeam(other);
            if (ot == null || ot == my) continue;

            if (other.getWorld() != p.getWorld()) continue;

            double d = other.getLocation().distanceSquared(p.getLocation());
            if (d < best) {
                best = d;
                bestP = other;
            }
        }

        return bestP;
    }
}
