package com.blake.portalplugin.listeners;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public class LegacyCombatListener implements Listener {

    private final Plugin plugin;
    private final GameStateManager gameStateManager;

    public LegacyCombatListener(Plugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
    }

    private boolean isLegacyPvpEnabled() {
        FileConfiguration cfg = plugin.getConfig();
        return cfg.getBoolean("legacy-combat.enabled", true);
    }

    private boolean isPvp(Player p) {
        return p != null && gameStateManager.getGameState(p) == GameState.PVP;
    }

    // ---------------------------------------------------------
    // No sweeping: cancel sweep damage (secondary targets)
    // ---------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSweep(EntityDamageByEntityEvent event) {
        if (!isLegacyPvpEnabled()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;

        if (event.getDamager() instanceof Player attacker) {
            if (isPvp(attacker)) {
                event.setCancelled(true);
            }
        }
    }

    // ---------------------------------------------------------
    // 1.8-ish crits + old knockback (override velocity)
    // ---------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (!isLegacyPvpEnabled()) return;

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!isPvp(attacker)) return;

        Entity rawVictim = event.getEntity();
        if (!(rawVictim instanceof LivingEntity victim)) return;

        // Only apply to melee "ENTITY_ATTACK" (not projectiles)
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        FileConfiguration cfg = plugin.getConfig();

        // ---- 1.8 style crit logic (approx) ----
        if (cfg.getBoolean("legacy-combat.crit.enabled", true)) {
            if (shouldClassicCrit(attacker)) {
                double mult = cfg.getDouble("legacy-combat.crit.multiplier", 1.5);
                event.setDamage(event.getDamage() * mult);

                if (cfg.getBoolean("legacy-combat.crit.effects", true)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            victim.getWorld().spawnParticle(
                                    Particle.CRIT,
                                    victim.getLocation().add(0, 1.0, 0),
                                    18,
                                    0.35, 0.35, 0.35,
                                    0.15
                            );
                            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
                        } catch (Throwable ignored) {}
                    });
                }
            }
        }

        // ---- old knockback (override vanilla velocity next tick) ----
        if (cfg.getBoolean("legacy-combat.knockback.enabled", true)) {
            int kbLevel = 0;
            try {
                kbLevel = attacker.getInventory().getItemInMainHand()
                        .getEnchantmentLevel(Enchantment.KNOCKBACK);
            } catch (Throwable ignored) {}

            final int finalKbLevel = kbLevel;
            Bukkit.getScheduler().runTask(plugin, () -> applyLegacyKnockback(attacker, victim, finalKbLevel));
        }
    }

    private boolean shouldClassicCrit(Player attacker) {
        // Approx classic crit conditions (1.8-ish):
        // - falling (fallDistance > 0) and not on ground
        // - not sprinting
        // - not in water/lava
        // - not climbing
        // - not inside vehicle
        // - not blind
        // (This won’t be 100% identical to 1.8, but is close.)
        try {
            if (attacker.isOnGround()) return false;
            if (attacker.getFallDistance() <= 0.0f) return false;
            if (attacker.isSprinting()) return false;
            if (attacker.isInsideVehicle()) return false;
            if (attacker.isClimbing()) return false;
            if (attacker.isInWater() || attacker.isInLava()) return false;
            if (attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)) return false;
            if (attacker.isGliding()) return false;
        } catch (Throwable ignored) {
            // If any API differs, fail safe: don't crit.
            return false;
        }
        return true;
    }

    private void applyLegacyKnockback(Player attacker, LivingEntity victim, int knockbackLevel) {
        if (attacker == null || victim == null) return;
        if (!attacker.isOnline()) return;
        if (victim.isDead()) return;
        if (!isPvp(attacker)) return;

        FileConfiguration cfg = plugin.getConfig();

        double baseH = cfg.getDouble("legacy-combat.knockback.baseHorizontal", 0.40);
        double baseY = cfg.getDouble("legacy-combat.knockback.baseVertical", 0.35);

        double sprintBonusH = cfg.getDouble("legacy-combat.knockback.sprintBonusHorizontal", 0.20);
        boolean sprintReset = cfg.getBoolean("legacy-combat.knockback.sprintResetOnHit", true);

        double kbBonusH = cfg.getDouble("legacy-combat.knockback.enchantHorizontalPerLevel", 0.50);
        double kbBonusY = cfg.getDouble("legacy-combat.knockback.enchantVerticalPerLevel", 0.05);

        boolean attackerSprinting = attacker.isSprinting();

        // Direction from attacker -> victim on XZ plane
        Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        dir.setY(0);

        if (dir.lengthSquared() < 0.0001) {
            // Fallback: use attacker's look direction
            dir = attacker.getLocation().getDirection();
            dir.setY(0);
        }

        if (dir.lengthSquared() < 0.0001) return;
        dir.normalize();

        double horizontal = baseH
                + (attackerSprinting ? sprintBonusH : 0.0)
                + (knockbackLevel * kbBonusH);

        double vertical = baseY + (knockbackLevel * kbBonusY);

        Vector vel = dir.multiply(horizontal);
        vel.setY(vertical);

        try {
            victim.setVelocity(vel);
        } catch (Throwable ignored) {}

        // 1.8-like sprint reset on hit
        if (sprintReset && attackerSprinting) {
            try {
                attacker.setSprinting(false);
            } catch (Throwable ignored) {}
        }
    }
}