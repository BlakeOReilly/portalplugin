package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class BlastMinigameManager {

    public static final int MAX_PLAYERS = 16;
    public static final int MAX_SECONDS = 20 * 60;

    private static final int STARTING_LIVES = 80;

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;

    private final BlastMapStore mapStore;

    private boolean inProgress = false;
    private BlastMap activeMap = null;

    private final List<UUID> participants = new ArrayList<>();
    private final Map<UUID, BlastTeam> teamByPlayer = new HashMap<>();
    private final Map<UUID, Integer> spawnIndexByPlayer = new HashMap<>();

    private final Map<BlastTeam, Integer> livesByTeam = new EnumMap<>(BlastTeam.class);
    private final Set<BlastTeam> eliminatedTeams = EnumSet.noneOf(BlastTeam.class);
    private final Map<BlastTeam, BlastSpawnProtectionRegion> spawnProtectionByTeam = new EnumMap<>(BlastTeam.class);

    private BlastGameTimerTask timerTask = null;
    private int secondsRemaining = MAX_SECONDS;

    // Elim Tokens (per-match, resets each game)
    private final Map<UUID, Integer> elimTokensByPlayer = new HashMap<>();
    private final Map<UUID, Integer> blastStreakByPlayer = new HashMap<>();

    // Shop NPCs (1 per team)
    private final Map<BlastTeam, UUID> shopNpcByTeam = new EnumMap<>(BlastTeam.class);

    public BlastMinigameManager(PortalPlugin plugin, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.gameStateManager = gameStateManager;
        this.mapStore = new BlastMapStore(plugin);
        resetLives();
        loadSpawnProtectionFromConfig();
    }

    private BlastPowerupManager getPowerupManagerSafe() {
        try {
            Method m = plugin.getClass().getMethod("getBlastPowerupManager");
            Object o = m.invoke(plugin);
            if (o instanceof BlastPowerupManager pm) return pm;
        } catch (Throwable ignored) {}
        return null;
    }

    public BlastMapStore getMapStore() {
        return mapStore;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public BlastMap getActiveMap() {
        return activeMap;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public int getLives(BlastTeam team) {
        if (team == null) return 0;
        return livesByTeam.getOrDefault(team, 0);
    }

    // COMPAT: ScoreboardManager calls this name.
    public int getTeamLives(BlastTeam team) {
        return getLives(team);
    }

    public int getElimTokens(Player p) {
        if (p == null) return 0;
        return elimTokensByPlayer.getOrDefault(p.getUniqueId(), 0);
    }

    public boolean trySpendElimTokens(Player p, int cost) {
        if (p == null || cost <= 0) return false;
        int cur = elimTokensByPlayer.getOrDefault(p.getUniqueId(), 0);
        if (cur < cost) return false;

        elimTokensByPlayer.put(p.getUniqueId(), cur - cost);
        refreshBoards();
        return true;
    }

    public void addTeamLives(BlastTeam team, int delta) {
        if (!inProgress) return;
        if (team == null) return;
        int cur = livesByTeam.getOrDefault(team, STARTING_LIVES);
        int next = Math.max(0, cur + delta);
        livesByTeam.put(team, next);
        refreshBoards();
    }

    public BlastTeam getTeam(Player p) {
        if (p == null) return null;
        return teamByPlayer.get(p.getUniqueId());
    }

    public boolean isParticipant(Player p) {
        return p != null && participants.contains(p.getUniqueId());
    }

    public boolean isBlastShopNpc(Entity e) {
        if (e == null) return false;
        if (!(e instanceof Villager)) return false;

        try {
            var key = BlastShopItems.npcKey(plugin);
            String teamKey = ((Villager) e).getPersistentDataContainer().get(key, PersistentDataType.STRING);
            return teamKey != null && !teamKey.isBlank();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void resetLives() {
        livesByTeam.clear();
        for (BlastTeam t : BlastTeam.values()) {
            livesByTeam.put(t, STARTING_LIVES);
        }
        eliminatedTeams.clear();
    }

    private void resetElimTokens() {
        elimTokensByPlayer.clear();
    }

    private void resetBlastStreaks() {
        blastStreakByPlayer.clear();
    }

    private void loadSpawnProtectionFromConfig() {
        spawnProtectionByTeam.clear();

        var root = plugin.getConfig().getConfigurationSection("blast.spawn-protection");
        if (root == null) return;

        for (BlastTeam team : BlastTeam.values()) {
            var section = root.getConfigurationSection(team.getKey());
            if (section == null) continue;

            String world = section.getString("world");
            if (world == null || world.isBlank()) continue;

            double x1 = section.getDouble("x1");
            double y1 = section.getDouble("y1");
            double z1 = section.getDouble("z1");
            double x2 = section.getDouble("x2");
            double y2 = section.getDouble("y2");
            double z2 = section.getDouble("z2");

            spawnProtectionByTeam.put(team, new BlastSpawnProtectionRegion(world, x1, y1, z1, x2, y2, z2));
        }
    }

    public void setSpawnProtection(BlastTeam team, Location a, Location b) {
        if (team == null || a == null || b == null) return;
        if (a.getWorld() == null || b.getWorld() == null) return;
        if (!a.getWorld().getName().equals(b.getWorld().getName())) return;

        String world = a.getWorld().getName();
        spawnProtectionByTeam.put(team, new BlastSpawnProtectionRegion(
                world,
                a.getX(), a.getY(), a.getZ(),
                b.getX(), b.getY(), b.getZ()
        ));

        String path = "blast.spawn-protection." + team.getKey();
        plugin.getConfig().set(path + ".world", world);
        plugin.getConfig().set(path + ".x1", a.getX());
        plugin.getConfig().set(path + ".y1", a.getY());
        plugin.getConfig().set(path + ".z1", a.getZ());
        plugin.getConfig().set(path + ".x2", b.getX());
        plugin.getConfig().set(path + ".y2", b.getY());
        plugin.getConfig().set(path + ".z2", b.getZ());
        plugin.saveConfig();
    }

    private boolean isSpawnProtected(Player victim) {
        if (victim == null || victim.getWorld() == null) return false;
        BlastTeam team = teamByPlayer.get(victim.getUniqueId());
        if (team == null) return false;

        BlastSpawnProtectionRegion region = spawnProtectionByTeam.get(team);
        if (region == null) return false;

        return region.contains(victim.getLocation());
    }

    private void awardElimToken(Player killer, Player victim, String weaponLabel) {
        if (!inProgress) return;

        if (killer != null) {
            if (!killer.isOnline()) return;
            if (victim != null && killer.getUniqueId().equals(victim.getUniqueId())) return;
            if (!participants.contains(killer.getUniqueId())) return;

            int now = elimTokensByPlayer.getOrDefault(killer.getUniqueId(), 0) + 1;
            elimTokensByPlayer.put(killer.getUniqueId(), now);

            if (victim != null) {
                broadcastToParticipants("§e[BLAST] §f" + killer.getName() + " §esent §f" + victim.getName() + " §eback to spawn.");
            }

            updateBlastStreak(killer);
        }

        refreshBoards();
    }

    private void updateBlastStreak(Player killer) {
        if (killer == null || !killer.isOnline()) return;
        int next = blastStreakByPlayer.getOrDefault(killer.getUniqueId(), 0) + 1;
        blastStreakByPlayer.put(killer.getUniqueId(), next);

        if (next % 5 == 0 && next <= 300) {
            broadcastToParticipants("§d[BLAST] §f" + killer.getName()
                    + " §dis on a blast streak of §f" + next + "§d!");
        }
    }

    private void resetBlastStreak(Player victim) {
        if (victim == null) return;
        blastStreakByPlayer.put(victim.getUniqueId(), 0);
    }

    private void sendHitMessage(Player shooter, Player victim, String weaponLabel) {
        if (shooter == null || victim == null) return;
        if (!victim.isOnline()) return;
        String weapon = (weaponLabel == null || weaponLabel.isBlank()) ? "Blaster" : weaponLabel;
        victim.sendMessage("§c[BLAST] You were hit by §f" + shooter.getName() + " §cwith §f" + weapon + "§c.");
    }

    private String weaponLabelForSource(BlastDamageSource source) {
        if (source == null) return "Blaster";
        return switch (source) {
            case STRIKE_BLASTER -> "Strike Blaster";
            case HOMING_MISSILE -> "Homing Missile";
            default -> "Blaster";
        };
    }

    public void reloadMaps() {
        if (tryInvokeNoArgs(mapStore, "reload")) return;
        if (tryInvokeNoArgs(mapStore, "reloadMaps")) return;
        if (tryInvokeNoArgs(mapStore, "load")) return;

        plugin.getLogger().warning("[PortalPlugin] BlastMapStore has no reload method (reload/reloadMaps/load).");
    }

    public void regenerateMap(String mapName, CommandSender sender) {
        if (sender == null) return;

        String chosen = (mapName == null ? "" : mapName.trim());
        if (chosen.isEmpty()) {
            chosen = plugin.getConfig().getString("blast.active-map", "");
        }
        if (chosen == null || chosen.isBlank()) {
            sender.sendMessage("§c[BLAST] No map specified. Use /blastregen <map> or set blast.active-map in config.yml");
            return;
        }

        if (tryInvokeTwoArgs(mapStore, "regenerateMap", String.class, CommandSender.class, chosen, sender)) return;
        if (tryInvokeTwoArgs(mapStore, "regenerate", String.class, CommandSender.class, chosen, sender)) return;
        if (tryInvokeTwoArgs(mapStore, "pasteMap", String.class, CommandSender.class, chosen, sender)) return;

        BlastMap map = mapStore.getMap(chosen);
        if (map == null) {
            sender.sendMessage("§c[BLAST] Unknown map '" + chosen + "'. Use /blastregen list");
            return;
        }

        sender.sendMessage("§e[BLAST] Map '" + map.getName() + "' is loaded, but regeneration is not implemented in BlastMapStore.");
        sender.sendMessage("§e[BLAST] Implement a store method like regenerateMap(String, CommandSender) to paste your schematic.");
    }

    public List<UUID> startFromQueue(List<Player> queuedPlayers) {
        if (queuedPlayers == null) return List.of();
        if (inProgress) return List.of();

        LinkedHashMap<UUID, Player> unique = new LinkedHashMap<>();
        for (Player p : queuedPlayers) {
            if (p == null || !p.isOnline()) continue;
            unique.putIfAbsent(p.getUniqueId(), p);
            if (unique.size() >= MAX_PLAYERS) break;
        }

        if (unique.size() < 2) return List.of();

        String mapName = plugin.getConfig().getString("blast.active-map", "");
        BlastMap map = resolveMap(mapName);
        if (map == null) {
            broadcastToPlayers(unique.values(), "§c[BLAST] No BLAST maps are configured. (blast-maps.yml is empty)");
            return List.of();
        }

        if (!map.hasEnoughSpawns()) {
            broadcastToPlayers(unique.values(),
                    "§c[BLAST] Map '" + map.getName() + "' is missing spawns. Need 4 spawns per team.");
            return List.of();
        }

        removeShopNpcs();

        this.inProgress = true;
        this.activeMap = map;
        this.secondsRemaining = MAX_SECONDS;

        participants.clear();
        teamByPlayer.clear();
        spawnIndexByPlayer.clear();
        resetLives();
        resetElimTokens();
        resetBlastStreaks();

        List<BlastTeam> order = List.of(BlastTeam.RED, BlastTeam.GREEN, BlastTeam.YELLOW, BlastTeam.BLUE);
        Map<BlastTeam, Integer> teamCounts = new EnumMap<>(BlastTeam.class);
        for (BlastTeam t : order) teamCounts.put(t, 0);

        int idx = 0;

        Scoreboard main = (Bukkit.getScoreboardManager() != null)
                ? Bukkit.getScoreboardManager().getMainScoreboard()
                : null;
        if (main != null) ensureTeamsExist(main);

        BlastPowerupManager pm = getPowerupManagerSafe();

        for (Player p : unique.values()) {
            participants.add(p.getUniqueId());
            elimTokensByPlayer.put(p.getUniqueId(), 0);

            // clean slate per match
            if (pm != null) {
                try { pm.reset(p); } catch (Throwable ignored) {}
            }

            BlastTeam team = order.get(idx % order.size());
            idx++;

            BlastTeam best = team;
            int bestCount = teamCounts.get(team);
            for (BlastTeam t : order) {
                int c = teamCounts.get(t);
                if (c < bestCount) {
                    best = t;
                    bestCount = c;
                }
            }
            team = best;

            int spawnIndex = teamCounts.get(team);
            teamCounts.put(team, spawnIndex + 1);

            teamByPlayer.put(p.getUniqueId(), team);
            spawnIndexByPlayer.put(p.getUniqueId(), spawnIndex);

            Location spawn = map.getSpawnFor(team, spawnIndex);
            if (spawn != null) p.teleport(spawn);

            setGameStateSafe(p, "BLAST", "ARENA");

            applyFreshBlastLoadout(p);

            p.sendMessage("§a[BLAST] You are on team " + team.getColor() + team.getKey().toUpperCase() + "§a.");
        }

        for (BlastTeam t : BlastTeam.values()) {
            if (teamCounts.getOrDefault(t, 0) > 0) continue;
            eliminateTeamNoPlayers(t, "has no players.");
        }

        applyBlastTeamsToAllParticipantScoreboards();
        spawnShopNpcsForTeams();

        timerTask = new BlastGameTimerTask(this);
        timerTask.runTaskTimer(plugin, 20L, 20L);

        broadcastToParticipants("§a[BLAST] Game starting! Team lives: " + STARTING_LIVES + " each.");
        refreshBoards();

        return new ArrayList<>(unique.keySet());
    }

    // =========================
    // RESTORED METHODS (for BlastBlasterListener / Utility)
    // =========================

    // Basic hit: remove armor pieces; if no armor remains, elim + respawn
    public void applyBasicHit(Player shooter, Player victim, Location impact) {
        if (!inProgress) return;
        if (shooter == null || victim == null) return;
        if (!shooter.isOnline() || !victim.isOnline()) return;
        if (!isParticipant(shooter) || !isParticipant(victim)) return;
        if (!canDamage(victim, BlastDamageSource.DEFAULT)) return;
        if (sameTeam(shooter, victim)) return; // NO FRIENDLY FIRE

        BlastStrikeUtil.requestCancel(plugin, victim);
        applyBlasterPowerups(shooter, victim);

        BlastTeam victimTeam = teamByPlayer.get(victim.getUniqueId());
        if (victimTeam == null) return;

        int piecesToRemove = 1;
        BlastPowerupManager pm = getPowerupManagerSafe();
        if (pm != null) {
            try { piecesToRemove = pm.getBasicArmorPiecesToRemove(shooter); } catch (Throwable ignored) {}
        }

        // remove up to N armor pieces; if we run out of armor during removal -> eliminate
        for (int i = 0; i < Math.max(1, piecesToRemove); i++) {
            boolean removed = removeNextArmorPiece(victim);
            if (removed) continue;

            sendHitMessage(shooter, victim, "Basic Blaster");
            awardElimToken(shooter, victim, "Basic Blaster");
            respawnAndConsumeLife(victim, victimTeam);
            return;
        }

        // armor removed, but not eliminated
        sendHitMessage(shooter, victim, "Basic Blaster");
        refreshBoards();
    }

    // Big direct: always wipe armor then respawn/consume life
    public void applyBigDirectHit(Player shooter, Player victim, Location impact) {
        if (!inProgress) return;
        if (shooter == null || victim == null) return;
        if (!shooter.isOnline() || !victim.isOnline()) return;
        if (!isParticipant(shooter) || !isParticipant(victim)) return;
        if (!canDamage(victim, BlastDamageSource.DEFAULT)) return;
        if (sameTeam(shooter, victim)) return; // NO FRIENDLY FIRE

        BlastStrikeUtil.requestCancel(plugin, victim);
        applyBlasterPowerups(shooter, victim);

        BlastTeam victimTeam = teamByPlayer.get(victim.getUniqueId());
        if (victimTeam == null) return;

        sendHitMessage(shooter, victim, "Big Blaster");
        awardElimToken(shooter, victim, "Big Blaster");

        wipeAllArmor(victim);
        respawnAndConsumeLife(victim, victimTeam);
    }

    // Big AoE: hits all enemies in radius; removes 1 armor piece; if none, elim+respawn
    public void applyBigAoE(Player shooter, Location center, double radius, Set<UUID> processed) {
        applyBigAoE(shooter, center, radius, processed, "Big Blaster");
    }

    public void applyBigAoE(Player shooter, Location center, double radius, Set<UUID> processed, String weaponLabel) {
        if (!inProgress) return;
        if (center == null || center.getWorld() == null) return;

        BlastTeam shooterTeam = (shooter != null) ? teamByPlayer.get(shooter.getUniqueId()) : null;
        double r2 = radius * radius;

        for (UUID id : new ArrayList<>(participants)) {
            if (processed != null && processed.contains(id)) continue;

            Player victim = Bukkit.getPlayer(id);
            if (victim == null || !victim.isOnline()) continue;
            if (gameStateManager.getGameState(victim) != GameState.BLAST) continue;

            if (victim.getWorld() != center.getWorld()) continue;
            if (victim.getLocation().distanceSquared(center) > r2) continue;
            if (!canDamage(victim, BlastDamageSource.DEFAULT)) continue;

            BlastTeam victimTeam = teamByPlayer.get(victim.getUniqueId());
            if (victimTeam == null) continue;

            if (shooter != null) {
                if (!isParticipant(shooter)) continue;
                if (shooterTeam != null && shooterTeam == victimTeam) continue; // NO FRIENDLY FIRE
            }

            BlastStrikeUtil.requestCancel(plugin, victim);
            applyBlasterPowerups(shooter, victim);

            boolean removedArmor = removeNextArmorPiece(victim);
            if (removedArmor) {
                sendHitMessage(shooter, victim, weaponLabel);
                if (processed != null) processed.add(id);
                continue;
            }

            sendHitMessage(shooter, victim, weaponLabel);
            awardElimToken(shooter, victim, weaponLabel);
            respawnAndConsumeLife(victim, victimTeam);

            if (processed != null) processed.add(id);
        }
    }

    // =========================

    private boolean canDamage(Player victim, BlastDamageSource source) {
        if (victim == null) return false;
        if (gameStateManager.getGameState(victim) != GameState.BLAST) return false;

        BlastDamageSource resolved = (source != null) ? source : BlastDamageSource.DEFAULT;
        if (isSpawnProtected(victim) && !resolved.bypassesSpawnProtection()) return false;

        return true;
    }

    private void applyBlasterPowerups(Player shooter, Player victim) {
        if (shooter == null || victim == null) return;
        if (!shooter.isOnline() || !victim.isOnline()) return;

        BlastPowerupManager pm = getPowerupManagerSafe();
        if (pm == null) return;

        int knockbackStacks = pm.getStacks(shooter, BlastPowerupType.KNOCKBACK);
        if (knockbackStacks > 0) {
            double strength = (0.40 + (Math.min(3, knockbackStacks) - 1) * 0.20) * 2.0;
            double vertical = (0.10 + (Math.min(3, knockbackStacks) - 1) * 0.05) * 2.0;

            Vector dir = victim.getLocation().toVector().subtract(shooter.getLocation().toVector());
            if (dir.lengthSquared() < 0.0001) {
                dir = shooter.getLocation().getDirection();
            }
            dir = dir.normalize().multiply(strength);

            Vector velocity = new Vector(dir.getX(), vertical, dir.getZ());
            victim.setVelocity(velocity);
        }

        int slowStacks = pm.getStacks(shooter, BlastPowerupType.SLOW_SHOT);
        if (slowStacks > 0) {
            int duration;
            int amplifier;
            switch (Math.min(3, slowStacks)) {
                case 1 -> {
                    duration = 10;
                    amplifier = 0;
                }
                case 2 -> {
                    duration = 20;
                    amplifier = 1;
                }
                default -> {
                    duration = 20;
                    amplifier = 3;
                }
            }
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier, false, false, true));
        }

        int markStacks = pm.getStacks(shooter, BlastPowerupType.MARK_TARGET);
        if (markStacks > 0) {
            int duration = switch (Math.min(3, markStacks)) {
                case 1 -> 60;
                case 2 -> 100;
                default -> 160;
            };
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, false, false, true));
        }

        int blindStacks = pm.getStacks(shooter, BlastPowerupType.BLIND_SHOT);
        if (blindStacks > 0 && ThreadLocalRandom.current().nextDouble() < 0.35) {
            int duration = switch (Math.min(3, blindStacks)) {
                case 1 -> 20;
                case 2 -> 40;
                default -> 60;
            };
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 0, false, false, true));
        }
    }

    private void applyFreshBlastLoadout(Player p) {
        if (p == null) return;

        try {
            clearInventoryFully(p);

            p.getInventory().setItem(0, BlastItems.createBasicBlaster(plugin));

            try { p.getInventory().setHeldItemSlot(0); } catch (Throwable ignored) {}

            // Powerup UI/effects must persist across respawn teleports
            try { gameStateManager.syncBlastPowerupUi(p); } catch (Throwable ignored) {}
            BlastPowerupManager pm = getPowerupManagerSafe();
            if (pm != null) {
                try { pm.applyEffects(p); } catch (Throwable ignored) {}
            }

            p.updateInventory();
        } catch (Throwable ignored) {}
    }

    private void clearInventoryFully(Player p) {
        if (p == null) return;

        try {
            PlayerInventory inv = p.getInventory();

            inv.clear();

            try { inv.setHelmet(null); } catch (Throwable ignored) {}
            try { inv.setChestplate(null); } catch (Throwable ignored) {}
            try { inv.setLeggings(null); } catch (Throwable ignored) {}
            try { inv.setBoots(null); } catch (Throwable ignored) {}

            try { inv.setItemInOffHand(null); } catch (Throwable ignored) {}

        } catch (Throwable ignored) {}
    }

    private void setCanPickupItemsSafe(Player p, boolean canPickup) {
        if (p == null) return;
        try {
            Method m = p.getClass().getMethod("setCanPickupItems", boolean.class);
            m.invoke(p, canPickup);
        } catch (Throwable ignored) {}
    }

    // ===== Friendly fire + name colors enforcement =====

    private boolean sameTeam(Player a, Player b) {
        if (a == null || b == null) return false;
        BlastTeam ta = teamByPlayer.get(a.getUniqueId());
        BlastTeam tb = teamByPlayer.get(b.getUniqueId());
        return ta != null && ta == tb;
    }

    private void applyBlastTeamsToAllParticipantScoreboards() {
        if (!inProgress) return;

        List<Player> online = new ArrayList<>();
        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) online.add(p);
        }

        for (Player viewer : online) {
            org.bukkit.scoreboard.Scoreboard sb = viewer.getScoreboard();
            if (sb == null) continue;

            ensureTeamsExist(sb);

            for (BlastTeam t : BlastTeam.values()) {
                Team team = sb.getTeam(t.getScoreboardTeamName());
                if (team == null) continue;
                for (Player p : online) {
                    try { team.removeEntry(p.getName()); } catch (Throwable ignored) {}
                }
            }

            for (Player p : online) {
                BlastTeam t = teamByPlayer.get(p.getUniqueId());
                if (t == null) continue;

                Team team = sb.getTeam(t.getScoreboardTeamName());
                if (team == null) {
                    team = sb.registerNewTeam(t.getScoreboardTeamName());
                    configureTeam(team, t);
                }
                try { team.addEntry(p.getName()); } catch (Throwable ignored) {}
            }
        }

        for (Player p : online) {
            BlastTeam t = teamByPlayer.get(p.getUniqueId());
            if (t == null) continue;
            try {
                p.setPlayerListName(t.getColor() + p.getName());
            } catch (Throwable ignored) {}
        }
    }

    private void ensureTeamsExist(org.bukkit.scoreboard.Scoreboard sb) {
        for (BlastTeam t : BlastTeam.values()) {
            Team team = sb.getTeam(t.getScoreboardTeamName());
            if (team == null) {
                team = sb.registerNewTeam(t.getScoreboardTeamName());
            }
            configureTeam(team, t);
        }
    }

    private void configureTeam(Team team, BlastTeam t) {
        if (team == null || t == null) return;

        try { team.setAllowFriendlyFire(false); } catch (Throwable ignored) {}
        try { team.setCanSeeFriendlyInvisibles(true); } catch (Throwable ignored) {}

        try { team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS); } catch (Throwable ignored) {}
        try { team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER); } catch (Throwable ignored) {}

        try {
            Method m = team.getClass().getMethod("setColor", org.bukkit.ChatColor.class);
            m.invoke(team, t.getColor());
        } catch (Throwable ignored) {}

        try { team.setPrefix(t.getColor().toString()); } catch (Throwable ignored) {}
    }

    // ===== Shop NPCs =====

    private void spawnShopNpcsForTeams() {
        if (!inProgress || activeMap == null) return;

        for (BlastTeam t : BlastTeam.values()) {
            Location base = activeMap.getSpawnFor(t, 0);
            if (base == null || base.getWorld() == null) continue;

            Location loc = base.clone().add(0.5, 0.0, 0.5);

            Villager v;
            try {
                v = loc.getWorld().spawn(loc, Villager.class);
            } catch (Throwable ex) {
                plugin.getLogger().log(Level.WARNING, "[BLAST] Failed to spawn shop villager for " + t, ex);
                continue;
            }

            try {
                v.getPersistentDataContainer().set(BlastShopItems.npcKey(plugin), PersistentDataType.STRING, t.getKey());
            } catch (Throwable ignored) {}

            try { v.setAI(false); } catch (Throwable ignored) {}
            try { v.setInvulnerable(true); } catch (Throwable ignored) {}
            try { v.setCollidable(false); } catch (Throwable ignored) {}
            try { v.setSilent(true); } catch (Throwable ignored) {}
            try { v.setCustomName(t.getColor() + "§lTEAM SHOP"); } catch (Throwable ignored) {}
            try { v.setCustomNameVisible(true); } catch (Throwable ignored) {}

            tryInvokeBoolean(v, "setRemoveWhenFarAway", false);
            tryInvokeBoolean(v, "setPersistent", true);

            shopNpcByTeam.put(t, v.getUniqueId());
        }
    }

    private void removeShopNpcs() {
        for (UUID id : new ArrayList<>(shopNpcByTeam.values())) {
            if (id == null) continue;
            Entity e = null;
            for (World w : Bukkit.getWorlds()) {
                try {
                    e = w.getEntity(id);
                    if (e != null) break;
                } catch (Throwable ignored) {}
            }
            if (e != null && !e.isDead()) {
                try { e.remove(); } catch (Throwable ignored) {}
            }
        }
        shopNpcByTeam.clear();
    }

    // ===== Elim / armor handling =====

    public void applyInstantElim(Player attacker, Player victim, BlastDamageSource source) {
        applyInstantElim(attacker, victim, source, weaponLabelForSource(source));
    }

    public void applyInstantElim(Player attacker, Player victim, BlastDamageSource source, String weaponLabel) {
        if (!inProgress) return;
        if (victim == null || !victim.isOnline()) return;
        if (!isParticipant(victim)) return;
        if (!canDamage(victim, source)) return;

        if (attacker != null) {
            if (!attacker.isOnline()) return;
            if (!isParticipant(attacker)) return;
            if (sameTeam(attacker, victim)) return; // NO FRIENDLY FIRE
        }

        BlastStrikeUtil.requestCancel(plugin, victim);

        BlastTeam victimTeam = teamByPlayer.get(victim.getUniqueId());
        if (victimTeam == null) return;

        sendHitMessage(attacker, victim, weaponLabel);
        awardElimToken(attacker, victim, weaponLabel);

        wipeAllArmor(victim);
        respawnAndConsumeLife(victim, victimTeam);
    }

    // COMPAT: older callers
    public void applyInstantElim(Player victim) {
        applyInstantElim(null, victim, BlastDamageSource.DEFAULT, weaponLabelForSource(BlastDamageSource.DEFAULT));
    }

    public void applyInstantElim(Player attacker, Player victim) {
        applyInstantElim(attacker, victim, BlastDamageSource.DEFAULT, weaponLabelForSource(BlastDamageSource.DEFAULT));
    }

    private void wipeAllArmor(Player victim) {
        if (victim == null) return;

        victim.getInventory().setHelmet(null);
        victim.getInventory().setChestplate(null);
        victim.getInventory().setLeggings(null);
        victim.getInventory().setBoots(null);
        victim.updateInventory();
    }

    private boolean removeNextArmorPiece(Player victim) {
        if (victim == null) return false;

        var inv = victim.getInventory();

        var helm = inv.getHelmet();
        if (helm != null && !helm.getType().isAir()) {
            inv.setHelmet(null);
            victim.updateInventory();
            return true;
        }

        var chest = inv.getChestplate();
        if (chest != null && !chest.getType().isAir()) {
            inv.setChestplate(null);
            victim.updateInventory();
            return true;
        }

        var legs = inv.getLeggings();
        if (legs != null && !legs.getType().isAir()) {
            inv.setLeggings(null);
            victim.updateInventory();
            return true;
        }

        var boots = inv.getBoots();
        if (boots != null && !boots.getType().isAir()) {
            inv.setBoots(null);
            victim.updateInventory();
            return true;
        }

        return false;
    }

    private void respawnAndConsumeLife(Player victim, BlastTeam victimTeam) {
        if (victim == null || victimTeam == null) return;

        resetBlastStreak(victim);

        if (eliminatedTeams.contains(victimTeam) || livesByTeam.getOrDefault(victimTeam, STARTING_LIVES) <= 0) {
            setTeamSpectator(victimTeam);
            return;
        }

        int old = livesByTeam.getOrDefault(victimTeam, STARTING_LIVES);
        int now = Math.max(0, old - 1);
        livesByTeam.put(victimTeam, now);

        if (now <= 0) {
            setTeamSpectator(victimTeam);
            refreshBoards();
            checkForLastTeamStanding();
            return;
        }

        int idx = spawnIndexByPlayer.getOrDefault(victim.getUniqueId(), 0);
        Location spawn = (activeMap != null) ? activeMap.getSpawnFor(victimTeam, idx) : null;
        if (spawn == null && activeMap != null) spawn = activeMap.getSpawnFor(victimTeam, 0);

        setCanPickupItemsSafe(victim, false);

        if (spawn != null) victim.teleport(spawn);

        applyFreshBlastLoadout(victim);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!inProgress) return;
            if (!isParticipant(victim)) return;
            applyFreshBlastLoadout(victim);
        }, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!inProgress) return;
            if (!isParticipant(victim)) return;
            applyFreshBlastLoadout(victim);
        }, 10L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> setCanPickupItemsSafe(victim, true), 20L);

        applyBlastTeamsToAllParticipantScoreboards();
        refreshBoards();
    }

    private void setTeamSpectator(BlastTeam team) {
        if (team == null) return;

        boolean newlyEliminated = eliminatedTeams.add(team);
        if (newlyEliminated) {
            broadcastToParticipants("§c[BLAST] Team " + team.getColor() + team.getKey().toUpperCase() + "§c is out of lives!");
        }

        for (UUID id : new ArrayList<>(participants)) {
            BlastTeam t = teamByPlayer.get(id);
            if (t != team) continue;

            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            setGameStateSafe(p, "SPECTATOR", "HUB");
        }
    }

    private void eliminateTeamNoPlayers(BlastTeam team, String reason) {
        if (team == null) return;

        livesByTeam.put(team, 0);
        boolean newlyEliminated = eliminatedTeams.add(team);
        if (newlyEliminated) {
            String msg = (reason == null || reason.isBlank())
                    ? "has no players remaining."
                    : reason;
            broadcastToParticipants("§c[BLAST] Team " + team.getColor()
                    + team.getKey().toUpperCase() + "§c " + msg);
        }

        refreshBoards();
        checkForLastTeamStanding();
    }

    private void checkForLastTeamStanding() {
        BlastTeam last = getLastTeamStanding();
        if (last == null) return;
        endGame("Team " + last.getColor() + last.getKey().toUpperCase() + "§a wins!");
    }

    private BlastTeam getLastTeamStanding() {
        BlastTeam remaining = null;
        int aliveCount = 0;

        for (BlastTeam team : BlastTeam.values()) {
            if (livesByTeam.getOrDefault(team, 0) <= 0) continue;
            aliveCount++;
            remaining = team;
        }

        return aliveCount == 1 ? remaining : null;
    }

    private void refreshBoards() {
        try {
            if (plugin.getScoreboardManager() != null) plugin.getScoreboardManager().refreshAll();
        } catch (Throwable ignored) {}

        try { applyBlastTeamsToAllParticipantScoreboards(); } catch (Throwable ignored) {}
    }

    public void handlePlayerQuit(Player p) {
        if (p == null) return;

        UUID id = p.getUniqueId();
        BlastTeam team = teamByPlayer.get(id);

        participants.remove(id);
        elimTokensByPlayer.remove(id);
        blastStreakByPlayer.remove(id);

        teamByPlayer.remove(id);
        spawnIndexByPlayer.remove(id);

        // remove effects/stacks for quitters too
        BlastPowerupManager pm = getPowerupManagerSafe();
        if (pm != null) {
            try { pm.reset(p); } catch (Throwable ignored) {}
        }

        if (inProgress) {
            applyBlastTeamsToAllParticipantScoreboards();
        }

        if (inProgress) {
            if (team != null) {
                boolean hasTeamPlayers = false;
                for (UUID pid : new ArrayList<>(participants)) {
                    BlastTeam t = teamByPlayer.get(pid);
                    if (t == team) {
                        hasTeamPlayers = true;
                        break;
                    }
                }
                if (!hasTeamPlayers) {
                    eliminateTeamNoPlayers(team, "has no players remaining.");
                }
            }

            int onlineLeft = 0;
            for (UUID pid : participants) {
                Player op = Bukkit.getPlayer(pid);
                if (op != null && op.isOnline()) onlineLeft++;
            }
            if (onlineLeft < 2) {
                endGame("Not enough players remaining.");
            }
        }
    }

    public void endGame(String reason) {
        if (!inProgress) return;

        broadcastToParticipants("§c[BLAST] " + (reason == null ? "Game ended." : reason));

        if (timerTask != null) {
            try { timerTask.cancel(); } catch (Exception ignored) {}
            timerTask = null;
        }

        removeShopNpcs();

        var hubSpawn = plugin.getHubSpawnManager() != null ? plugin.getHubSpawnManager().getHubSpawn() : null;

        BlastPowerupManager pm = getPowerupManagerSafe();

        for (UUID pid : new ArrayList<>(participants)) {
            Player pl = Bukkit.getPlayer(pid);
            if (pl == null || !pl.isOnline()) continue;

            if (pm != null) {
                try { pm.reset(pl); } catch (Throwable ignored) {}
            }

            try { pl.setPlayerListName(pl.getName()); } catch (Throwable ignored) {}

            if (hubSpawn != null) pl.teleport(hubSpawn);
            setGameStateSafe(pl, "HUB", "HUB");
        }

        participants.clear();
        teamByPlayer.clear();
        spawnIndexByPlayer.clear();
        resetElimTokens();
        resetBlastStreaks();

        inProgress = false;
        activeMap = null;
        secondsRemaining = MAX_SECONDS;

        resetLives();
        refreshBoards();

        MinigameQueueManager queueManager = plugin.getMinigameQueueManager();
        if (queueManager != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online == null || !online.isOnline()) continue;
                queueManager.handleJoin(online);
            }
        }
    }

    void tickSecond() {
        if (!inProgress) return;

        secondsRemaining--;
        if (secondsRemaining <= 0) {
            handleTimeLimitEnd();
            return;
        }

        if (secondsRemaining % 60 == 0) {
            int mins = secondsRemaining / 60;
            broadcastToParticipants("§e[BLAST] " + mins + " minute(s) remaining.");
        }

        refreshBoards();
    }

    private void handleTimeLimitEnd() {
        BlastTeam winner = resolveTimeLimitWinner();
        if (winner != null) {
            endGame("Time limit reached. Team " + winner.getColor() + winner.getKey().toUpperCase() + "§a wins!");
            return;
        }

        endGame("Time limit reached. Everyone loses.");
    }

    private BlastTeam resolveTimeLimitWinner() {
        Map<BlastTeam, Integer> elimTotals = getTeamElimTotals();

        int maxElims = -1;
        List<BlastTeam> elimLeaders = new ArrayList<>();
        for (BlastTeam team : BlastTeam.values()) {
            int elims = elimTotals.getOrDefault(team, 0);
            if (elims > maxElims) {
                maxElims = elims;
                elimLeaders.clear();
                elimLeaders.add(team);
            } else if (elims == maxElims) {
                elimLeaders.add(team);
            }
        }

        if (elimLeaders.size() == 1) return elimLeaders.get(0);

        int maxLives = -1;
        List<BlastTeam> lifeLeaders = new ArrayList<>();
        for (BlastTeam team : elimLeaders) {
            int lives = livesByTeam.getOrDefault(team, 0);
            if (lives > maxLives) {
                maxLives = lives;
                lifeLeaders.clear();
                lifeLeaders.add(team);
            } else if (lives == maxLives) {
                lifeLeaders.add(team);
            }
        }

        return lifeLeaders.size() == 1 ? lifeLeaders.get(0) : null;
    }

    private Map<BlastTeam, Integer> getTeamElimTotals() {
        Map<BlastTeam, Integer> totals = new EnumMap<>(BlastTeam.class);
        for (BlastTeam team : BlastTeam.values()) totals.put(team, 0);

        for (UUID id : new ArrayList<>(participants)) {
            BlastTeam team = teamByPlayer.get(id);
            if (team == null) continue;
            int cur = totals.getOrDefault(team, 0);
            int add = elimTokensByPlayer.getOrDefault(id, 0);
            totals.put(team, cur + add);
        }

        return totals;
    }

    private BlastMap resolveMap(String mapName) {
        BlastMap map = null;
        if (mapName != null && !mapName.isBlank()) {
            map = mapStore.getMap(mapName);
        }
        if (map == null) {
            map = mapStore.getFirstMapOrNull();
        }
        return map;
    }

    private void broadcastToParticipants(String msg) {
        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private void broadcastToPlayers(Collection<Player> players, String msg) {
        if (players == null) return;
        for (Player p : players) {
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
    }

    private void setGameStateSafe(Player p, String preferred, String fallback) {
        if (p == null) return;

        try {
            GameState st = GameState.valueOf(preferred.trim().toUpperCase());
            gameStateManager.setGameState(p, st);
            return;
        } catch (Exception ignored) {}

        try {
            GameState st = GameState.valueOf(fallback.trim().toUpperCase());
            gameStateManager.setGameState(p, st);
        } catch (Exception ignored) {}
    }

    private boolean tryInvokeNoArgs(Object target, String methodName) {
        if (target == null || methodName == null) return false;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.invoke(target);
            return true;
        } catch (Throwable t) {
            if (t instanceof java.lang.reflect.InvocationTargetException) {
                plugin.getLogger().log(Level.WARNING, "[PortalPlugin] BlastMapStore." + methodName + "() failed", t);
            }
            return false;
        }
    }

    private boolean tryInvokeTwoArgs(
            Object target,
            String methodName,
            Class<?> a1,
            Class<?> a2,
            Object v1,
            Object v2
    ) {
        if (target == null || methodName == null) return false;
        try {
            Method m = target.getClass().getMethod(methodName, a1, a2);
            m.invoke(target, v1, v2);
            return true;
        } catch (Throwable t) {
            if (t instanceof java.lang.reflect.InvocationTargetException) {
                plugin.getLogger().log(Level.WARNING, "[PortalPlugin] BlastMapStore." + methodName + "(..,..) failed", t);
            }
            return false;
        }
    }

    private void tryInvokeBoolean(Object target, String methodName, boolean arg) {
        if (target == null || methodName == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, boolean.class);
            m.invoke(target, arg);
        } catch (Throwable ignored) {}
    }
}
