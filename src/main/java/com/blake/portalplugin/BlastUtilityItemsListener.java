package com.blake.portalplugin.listeners;

import com.blake.portalplugin.*;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Arrow;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlastUtilityItemsListener implements Listener {

    private static final String HOMING_TITLE = "§c§lHoming Missile";

    private final PortalPlugin plugin;
    private final GameStateManager gsm;
    private final Map<UUID, Long> enderSoarActive = new HashMap<>();
    private final Map<UUID, TargetMode> targetModeByPlayer = new HashMap<>();

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
                openTargetMenu(p, bm, TargetMode.TRACKER);
            }
            case "HOMING" -> {
                e.setCancelled(true);
                openTargetMenu(p, bm, TargetMode.HOMING);
            }
            case "ENDER_SOAR" -> {
                e.setCancelled(true);
                if (consumeOneFromHand(p)) {
                    launchEnderSoar(p);
                }
            }
            case "TUNNELER" -> {
                e.setCancelled(true);
                if (consumeOneFromHand(p)) {
                    Block clicked = e.getClickedBlock();
                    if (clicked != null) {
                        digWoolTunnel(p, clicked.getLocation());
                    } else {
                        digWoolTunnel(p, p.getLocation().getBlock().getLocation());
                    }
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

        Location base = p.getLocation().getBlock().getRelative(0, -1, 0).getLocation();
        Vector dir = p.getLocation().getDirection().setY(0).normalize();

        // Block in front of player
        Location front = base.clone().add(dir.clone().multiply(1.0)).add(0, 1, 0);

        // Determine perpendicular axis for width
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        int height = 3; // 3 high
        int length = 10; // 10 long

        int placed = 0;

        for (int l = 1; l <= length; l++) {
            Location along = front.clone().add(dir.clone().multiply(l - 1));
            for (int w = -1; w <= 0; w++) {
                for (int h = 0; h < height; h++) {
                    Location at = along.clone().add(right.clone().multiply(w)).add(0, h, 0);
                    var block = at.getBlock();

                    if (block.getType().isAir()) {
                        block.setType(wool, false);
                        placed++;
                    }
                }
            }
        }

        p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.8f, 1.1f);
        p.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 10, 0.4, 0.2, 0.4, 0.01);

        if (placed == 0) {
            p.sendMessage("§c[BLAST] No space to place the wall.");
        }
    }

    private void openTargetMenu(Player p, BlastMinigameManager bm, TargetMode mode) {
        List<Player> targets = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == null || !other.isOnline()) continue;
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            if (gsm.getGameState(other) != GameState.BLAST) continue;
            if (!bm.isParticipant(other)) continue;

            BlastTeam my = bm.getTeam(p);
            BlastTeam ot = bm.getTeam(other);
            if (my != null && ot != null && my == ot) continue;

            targets.add(other);
        }

        if (targets.isEmpty()) {
            p.sendMessage("§c[BLAST] No enemy players to target.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, HOMING_TITLE);
        targetModeByPlayer.put(p.getUniqueId(), mode);

        int index = 0;
        for (Player target : targets) {
            int column = index % 9;
            int rowGroup = (index / 9) * 2;
            if (rowGroup + 1 >= 6) break;

            BlastTeam team = bm.getTeam(target);
            Material wool = (team != null) ? BlastItems.getTeamWool(team) : Material.WHITE_WOOL;

            ItemStack woolItem = new ItemStack(wool, 1);
            ItemMeta woolMeta = woolItem.getItemMeta();
            if (woolMeta != null) {
                woolMeta.setDisplayName("§7Team: " + (team != null ? team.getColor() + team.getKey().toUpperCase() : "§fUNKNOWN"));
                woolItem.setItemMeta(woolMeta);
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta) {
                SkullMeta skullMeta = (SkullMeta) meta;
                skullMeta.setOwningPlayer(target);
                skullMeta.setDisplayName("§cTarget: §f" + target.getName());
                skullMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "blast_homing_target"),
                        PersistentDataType.STRING, target.getUniqueId().toString());
                head.setItemMeta(skullMeta);
            }

            inv.setItem(rowGroup * 9 + column, woolItem);
            inv.setItem((rowGroup + 1) * 9 + column, head);
            index++;
        }

        p.openInventory(inv);
    }

    @EventHandler
    public void onHomingMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getView() == null || !HOMING_TITLE.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String targetId = pdc.get(new NamespacedKey(plugin, "blast_homing_target"), PersistentDataType.STRING);
        if (targetId == null) return;

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;
        if (gsm.getGameState(p) != GameState.BLAST || !bm.isParticipant(p)) return;

        Player target = Bukkit.getPlayer(UUID.fromString(targetId));
        if (target == null || !target.isOnline()) {
            p.sendMessage("§c[BLAST] That target is no longer available.");
            p.closeInventory();
            return;
        }

        TargetMode mode = targetModeByPlayer.getOrDefault(p.getUniqueId(), TargetMode.HOMING);
        targetModeByPlayer.remove(p.getUniqueId());
        if (mode == TargetMode.HOMING) {
            if (!consumeItemById(p, "HOMING")) {
                p.sendMessage("§c[BLAST] You no longer have a homing missile.");
                p.closeInventory();
                return;
            }
        }

        p.closeInventory();
        if (mode == TargetMode.TRACKER) {
            p.setCompassTarget(target.getLocation());
            p.sendMessage("§e[BLAST] Tracking: §f" + target.getName());
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
        } else {
            launchHomingParticle(p, target, bm);
        }
    }

    @EventHandler
    public void onTargetMenuClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        if (e.getView() == null || !HOMING_TITLE.equals(e.getView().getTitle())) return;

        Player p = (Player) e.getPlayer();
        targetModeByPlayer.remove(p.getUniqueId());
    }

    private boolean consumeItemById(Player p, String id) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;

            String shopId = BlastShopItems.getShopId(plugin, item);
            if (shopId == null || !shopId.equalsIgnoreCase(id)) continue;

            if (item.getAmount() <= 1) {
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - 1);
                contents[i] = item;
            }
            p.getInventory().setContents(contents);
            p.updateInventory();
            return true;
        }
        return false;
    }

    private void launchHomingParticle(Player shooter, Player target, BlastMinigameManager bm) {
        Location start = shooter.getEyeLocation().clone();
        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.4f);

        shooter.playSound(shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.1f);

        new BukkitRunnable() {
            int ticks = 0;
            Location pos = start.clone();

            @Override
            public void run() {
                ticks++;
                if (ticks > 120) {
                    cancel();
                    return;
                }

                if (!shooter.isOnline() || !target.isOnline()) {
                    cancel();
                    return;
                }

                if (gsm.getGameState(shooter) != GameState.BLAST || gsm.getGameState(target) != GameState.BLAST) {
                    cancel();
                    return;
                }

                if (shooter.getWorld() != target.getWorld()) {
                    cancel();
                    return;
                }

                Vector to = target.getEyeLocation().toVector().subtract(pos.toVector());
                double dist = Math.max(0.2, to.length());
                Vector step = to.normalize().multiply(1.1);
                pos.add(step);

                pos.getWorld().spawnParticle(Particle.DUST, pos, 6, 0.05, 0.05, 0.05, 0, dust);

                if (dist < 1.3) {
                    bm.applyInstantElim(shooter, target);
                    target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 2, 0.2, 0.2, 0.2, 0);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void launchEnderSoar(Player p) {
        EnderPearl pearl = p.launchProjectile(EnderPearl.class);
        pearl.setShooter(p);
        pearl.getPersistentDataContainer().set(new NamespacedKey(plugin, "blast_ender_soar"),
                PersistentDataType.INTEGER, 1);

        enderSoarActive.put(p.getUniqueId(), System.currentTimeMillis());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!pearl.isValid()) return;
            if (!pearl.getPassengers().contains(p)) {
                pearl.addPassenger(p);
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> enderSoarActive.remove(p.getUniqueId()), 200L);

        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 0.8f, 1.2f);
    }

    private void digWoolTunnel(Player p, Location start) {
        if (start == null || start.getWorld() == null) return;
        Vector dir = p.getLocation().getDirection().setY(0).normalize();

        int carved = 0;

        for (int i = 0; i < 20; i++) {
            Location step = start.clone().add(dir.clone().multiply(i));
            Block lower = step.getBlock();
            Block upper = lower.getRelative(0, 1, 0);

            if (!BlastItems.isColoredWool(lower.getType())) {
                break;
            }

            lower.setType(Material.AIR, false);
            if (BlastItems.isColoredWool(upper.getType())) {
                upper.setType(Material.AIR, false);
            } else {
                carved++;
                break;
            }
            carved++;
        }

        if (carved == 0) {
            p.sendMessage("§c[BLAST] No wool to tunnel through.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_WOOL_BREAK, 0.8f, 1.1f);
            p.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 12, 0.4, 0.2, 0.4, 0.01);
        }
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
        if (proj instanceof Fireball) {
            Fireball fb = (Fireball) proj;
            Integer mark = null;
            try {
                mark = fb.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "blast_fireball"), org.bukkit.persistence.PersistentDataType.INTEGER);
            } catch (Throwable ignored) {}

            if (mark == null || mark != 1) return;

            Player shooter = (fb.getShooter() instanceof Player) ? (Player) fb.getShooter() : null;

            Location impact = fb.getLocation();
            impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 2, 0.2, 0.2, 0.2, 0);
            impact.getWorld().spawnParticle(Particle.FLAME, impact, 40, 0.8, 0.4, 0.8, 0.02);
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);

            // Small AoE elim
            bm.applyBigAoE(shooter, impact, 4.5, null);
            BlastWoolUtil.breakWoolBurst(impact, 2);

            try { fb.remove(); } catch (Throwable ignored) {}
            return;
        }

        if (proj instanceof Arrow) {
            Arrow arrow = (Arrow) proj;
            Integer mark = arrow.getPersistentDataContainer().get(new NamespacedKey(plugin, "blast_boom_slingshot"),
                    PersistentDataType.INTEGER);
            if (mark == null || mark != 1) return;

            Player shooter = (arrow.getShooter() instanceof Player) ? (Player) arrow.getShooter() : null;
            Location impact = arrow.getLocation();

            impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 10, 2.5, 2.5, 2.5, 0.02);
            impact.getWorld().spawnParticle(Particle.FLAME, impact, 120, 2.5, 1.5, 2.5, 0.02);
            impact.getWorld().spawnParticle(Particle.SMOKE, impact, 60, 2.5, 1.5, 2.5, 0.02);
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.9f);

            if (bm != null) {
                double radius = 20.0;
                for (Player victim : impact.getWorld().getPlayers()) {
                    if (victim == null || !victim.isOnline()) continue;
                    if (gsm.getGameState(victim) != GameState.BLAST) continue;
                    if (victim.getLocation().distanceSquared(impact) > radius * radius) continue;

                    bm.applyInstantElim(shooter, victim);
                }
            }

            try { arrow.remove(); } catch (Throwable ignored) {}
        }

        if (proj instanceof EnderPearl) {
            EnderPearl pearl = (EnderPearl) proj;
            handleEnderSoarImpact(pearl, bm);
        }
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        if (bm == null || !bm.isInProgress()) return;
        if (gsm.getGameState(p) != GameState.BLAST || !bm.isParticipant(p)) return;

        ItemStack bow = e.getBow();
        if (bow == null || bow.getType().isAir()) return;
        String id = BlastShopItems.getShopId(plugin, bow);
        if (id == null || !id.equalsIgnoreCase("BOOM_SLINGSHOT")) return;

        if (e.getProjectile() instanceof Arrow) {
            Arrow arrow = (Arrow) e.getProjectile();
            arrow.getPersistentDataContainer().set(new NamespacedKey(plugin, "blast_boom_slingshot"),
                    PersistentDataType.INTEGER, 1);
        }

        consumeItemById(p, "BOOM_SLINGSHOT");
    }

    @EventHandler
    public void onEnderSoarTeleportHandler(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Player p = e.getPlayer();
        if (p == null) return;

        Long started = enderSoarActive.get(p.getUniqueId());
        if (started == null) return;

        e.setCancelled(true);
    }

    private void consumeItemFromHandsById(Player p, String id) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main != null && !main.getType().isAir()) {
            String mainId = BlastShopItems.getShopId(plugin, main);
            if (id.equalsIgnoreCase(mainId)) {
                consumeOneFromHand(p);
                return;
            }
        }

        ItemStack off = p.getInventory().getItemInOffHand();
        if (off != null && !off.getType().isAir()) {
            String offId = BlastShopItems.getShopId(plugin, off);
            if (!id.equalsIgnoreCase(offId)) return;

            int amt = off.getAmount();
            if (amt <= 1) {
                p.getInventory().setItemInOffHand(null);
            } else {
                off.setAmount(amt - 1);
                p.getInventory().setItemInOffHand(off);
            }
            p.updateInventory();
        }
    }

    private void handleEnderSoarImpact(EnderPearl pearl, BlastMinigameManager bm) {
        if (pearl == null) return;
        Integer mark = pearl.getPersistentDataContainer().get(new NamespacedKey(plugin, "blast_ender_soar"),
                PersistentDataType.INTEGER);
        if (mark == null || mark != 1) return;

        Player shooter = (pearl.getShooter() instanceof Player) ? (Player) pearl.getShooter() : null;
        Location impact = pearl.getLocation();

        if (shooter != null) {
            enderSoarActive.remove(shooter.getUniqueId());
            pearl.removePassenger(shooter);
            Location safe = BlastLandingUtil.findSafeLanding(impact, shooter);
            shooter.teleport(safe);
        }

        impact.getWorld().spawnParticle(Particle.DUST, impact, 80, 2.0, 1.0, 2.0, 0,
                new Particle.DustOptions(Color.AQUA, 1.6f));
        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 6, 0.8, 0.4, 0.8, 0);
        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);

        if (bm != null && shooter != null) {
            bm.applyBigAoE(shooter, impact, 5.0, new HashSet<>());
        }

        try { pearl.remove(); } catch (Throwable ignored) {}
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

    private enum TargetMode {
        HOMING,
        TRACKER
    }
}
