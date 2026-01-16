// BlastMapCommand.java
package com.blake.portalplugin;

import com.blake.portalplugin.worldedit.SelectionManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BlastMapCommand implements CommandExecutor, TabCompleter {

    private final PortalPlugin plugin;

    public BlastMapCommand(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("portalplugin.blast.maps")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            BlastMinigameManager mgr = plugin.getBlastMinigameManager();
            if (mgr == null) {
                sender.sendMessage("§c[BLAST] Manager not available.");
                return true;
            }
            List<String> maps = mgr.getMapStore().listMapNames();
            sender.sendMessage("§e[BLAST] Maps: " + (maps.isEmpty() ? "(none)" : String.join(", ", maps)));
            return true;
        }

        if (sub.equals("create")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /blastmap create <name>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMinigameManager mgr = plugin.getBlastMinigameManager();
            if (mgr == null) {
                sender.sendMessage("§c[BLAST] Manager not available.");
                return true;
            }
            BlastMap map = new BlastMap(name);
            if (sender instanceof Player p) {
                map.setWorldName(p.getWorld().getName());
            }
            mgr.getMapStore().putMap(map);
            sender.sendMessage("§a[BLAST] Created map '" + name + "'.");
            return true;
        }

        if (sub.equals("info")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /blastmap info <name>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMinigameManager mgr = plugin.getBlastMinigameManager();
            if (mgr == null) {
                sender.sendMessage("§c[BLAST] Manager not available.");
                return true;
            }
            BlastMap map = mgr.getMapStore().getMap(name);
            if (map == null) {
                sender.sendMessage("§c[BLAST] Map not found: " + name);
                return true;
            }

            sender.sendMessage("§e[BLAST] Map: §f" + map.getName());
            sender.sendMessage("§eWorld: §f" + (map.getWorldName() == null ? "(none)" : map.getWorldName()));
            sender.sendMessage("§ePaste: §f" + (map.getPasteLocation() == null ? "(none)" : format(map.getPasteLocation())));
            sender.sendMessage("§eRegion: §f" + (map.getRegionMin() == null ? "(none)" : (format(map.getRegionMin()) + " -> " + format(map.getRegionMax()))));
            sender.sendMessage("§eBlocks: §f" + (map.getSavedBlocks() == null ? 0 : map.getSavedBlocks().size()));
            sender.sendMessage("§eCeiling Y: §f" + (map.getCeilingY() == null ? "(none)" : map.getCeilingY()));
            sender.sendMessage("§eStart Spawn: §f" + (map.getStartSpawn() == null ? "(none)" : format(map.getStartSpawn())));

            for (BlastTeam t : BlastTeam.values()) {
                sender.sendMessage("§eSpawns " + t.getKey() + ": §f" + countNonNull(map.getSpawns(t)) + "/4");
            }
            return true;
        }

        if (sub.equals("setceiling")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /blastmap setceiling <name> <y>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMinigameManager mgr = plugin.getBlastMinigameManager();
            if (mgr == null) {
                sender.sendMessage("§c[BLAST] Manager not available.");
                return true;
            }
            BlastMap map = mgr.getMapStore().getMap(name);
            if (map == null) {
                sender.sendMessage("§c[BLAST] Map not found: " + name);
                return true;
            }

            int y;
            try {
                y = Integer.parseInt(args[2]);
            } catch (Exception e) {
                sender.sendMessage("§c[BLAST] Y must be a number.");
                return true;
            }

            map.setCeilingY(y);
            mgr.getMapStore().putMap(map);

            sender.sendMessage("§a[BLAST] Set ceiling Y for '" + name + "' to " + y + ".");
            return true;
        }

        if (sub.equals("select")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /blastmap select <name>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMinigameManager mgr = plugin.getBlastMinigameManager();
            if (mgr == null) {
                sender.sendMessage("§c[BLAST] Manager not available.");
                return true;
            }
            BlastMap map = mgr.getMapStore().getMap(name);
            if (map == null) {
                sender.sendMessage("§c[BLAST] Map not found: " + name);
                return true;
            }

            plugin.getConfig().set("blast.active-map", map.getName());
            plugin.saveConfig();
            sender.sendMessage("§a[BLAST] Active map set to '" + map.getName() + "'.");
            return true;
        }

        // Remaining commands require a Player
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        BlastMinigameManager mgr = plugin.getBlastMinigameManager();
        if (mgr == null) {
            sender.sendMessage("§c[BLAST] Manager not available.");
            return true;
        }

        if (sub.equals("setpaste")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /blastmap setpaste <name>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMap map = requireMap(sender, mgr, name);
            if (map == null) return true;

            map.setPasteLocation(p.getLocation());
            mgr.getMapStore().putMap(map);

            sender.sendMessage("§a[BLAST] Paste location set for '" + name + "' to " + format(p.getLocation()));
            return true;
        }

        if (sub.equals("setspawn")) {
            if (args.length < 4) {
                sender.sendMessage("§cUsage: /blastmap setspawn <name> <red|green|yellow|blue> <1-4>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMap map = requireMap(sender, mgr, name);
            if (map == null) return true;

            BlastTeam team = BlastTeam.fromKey(args[2]);
            if (team == null) {
                sender.sendMessage("§c[BLAST] Team must be red/green/yellow/blue.");
                return true;
            }

            int idx;
            try {
                idx = Integer.parseInt(args[3]);
            } catch (Exception e) {
                sender.sendMessage("§c[BLAST] Index must be 1-4.");
                return true;
            }
            if (idx < 1 || idx > 4) {
                sender.sendMessage("§c[BLAST] Index must be 1-4.");
                return true;
            }

            map.setSpawn(team, idx - 1, p.getLocation());
            mgr.getMapStore().putMap(map);

            sender.sendMessage("§a[BLAST] Set spawn " + idx + " for " + team.getKey() + " on '" + name + "'.");
            return true;
        }

        if (sub.equals("startspawn")) {
            if (args.length < 5) {
                sender.sendMessage("§cUsage: /blastmap startspawn <name> <x> <y> <z>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMap map = requireMap(sender, mgr, name);
            if (map == null) return true;

            double x;
            double y;
            double z;
            try {
                x = Double.parseDouble(args[2]);
                y = Double.parseDouble(args[3]);
                z = Double.parseDouble(args[4]);
            } catch (Exception e) {
                sender.sendMessage("§c[BLAST] Coordinates must be numbers.");
                return true;
            }

            if (p.getWorld() == null) {
                sender.sendMessage("§c[BLAST] Your world is not loaded.");
                return true;
            }

            Location loc = new Location(
                    p.getWorld(),
                    x,
                    y,
                    z,
                    p.getLocation().getYaw(),
                    p.getLocation().getPitch()
            );
            map.setStartSpawn(loc);
            mgr.getMapStore().putMap(map);

            sender.sendMessage("§a[BLAST] Start spawn set for '" + name + "' to " + format(loc));
            return true;
        }

        if (sub.equals("saveregion")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /blastmap saveregion <name>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMap map = requireMap(sender, mgr, name);
            if (map == null) return true;

            SelectionManager sel = plugin.getSelectionManager();
            Location pos1 = (sel != null) ? sel.getPos1(p.getUniqueId()) : null;
            Location pos2 = (sel != null) ? sel.getPos2(p.getUniqueId()) : null;

            if (pos1 == null || pos2 == null) {
                sender.sendMessage("§c[BLAST] You must set /pos1 and /pos2 first.");
                return true;
            }
            if (pos1.getWorld() == null || pos2.getWorld() == null || !Objects.equals(pos1.getWorld(), pos2.getWorld())) {
                sender.sendMessage("§c[BLAST] pos1 and pos2 must be in the same world.");
                return true;
            }

            Location min = new Location(pos1.getWorld(),
                    Math.min(pos1.getBlockX(), pos2.getBlockX()),
                    Math.min(pos1.getBlockY(), pos2.getBlockY()),
                    Math.min(pos1.getBlockZ(), pos2.getBlockZ()));
            Location max = new Location(pos1.getWorld(),
                    Math.max(pos1.getBlockX(), pos2.getBlockX()),
                    Math.max(pos1.getBlockY(), pos2.getBlockY()),
                    Math.max(pos1.getBlockZ(), pos2.getBlockZ()));

            long dx = (long) (max.getBlockX() - min.getBlockX() + 1);
            long dy = (long) (max.getBlockY() - min.getBlockY() + 1);
            long dz = (long) (max.getBlockZ() - min.getBlockZ() + 1);
            long total = dx * dy * dz;

            int maxBlocks = plugin.getConfig().getInt("blast.region.max-blocks", 250000);
            boolean bypass = p.hasPermission("portalplugin.worldedit.bypasslimit");
            if (!bypass && total > maxBlocks) {
                sender.sendMessage("§c[BLAST] Selection too large: " + total + " blocks (limit " + maxBlocks + ").");
                return true;
            }

            List<BlastSavedBlock> saved = new ArrayList<>();
            World w = min.getWorld();

            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                    for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                        Block b = w.getBlockAt(x, y, z);
                        Material type = b.getType();
                        if (type == Material.AIR) continue;
                        saved.add(new BlastSavedBlock(
                                x - min.getBlockX(),
                                y - min.getBlockY(),
                                z - min.getBlockZ(),
                                type.name()
                        ));
                    }
                }
            }

            map.setWorldName(w.getName());
            map.setRegionMin(min);
            map.setRegionMax(max);
            map.setSavedBlocks(saved);

            mgr.getMapStore().putMap(map);

            // FIX: List has size() method, not size field
            sender.sendMessage("§a[BLAST] Saved region for '" + name + "'. Stored " + saved.size()
                    + " non-air blocks.");
            sender.sendMessage("§e[BLAST] Tip: now stand at the paste origin and run /blastmap setpaste " + name);
            return true;
        }

        if (sub.equals("regen")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /blastmap regen <name>");
                return true;
            }
            String name = args[1].trim().toLowerCase();
            BlastMap map = requireMap(sender, mgr, name);
            if (map == null) return true;

            if (map.getPasteLocation() == null) {
                sender.sendMessage("§c[BLAST] This map has no paste location. Use /blastmap setpaste <name>.");
                return true;
            }
            if (map.getSavedBlocks() == null || map.getSavedBlocks().isEmpty()) {
                sender.sendMessage("§c[BLAST] This map has no saved region blocks. Use /blastmap saveregion <name>.");
                return true;
            }

            Location paste = map.getPasteLocation();
            if (paste.getWorld() == null) {
                sender.sendMessage("§c[BLAST] Paste world is missing.");
                return true;
            }

            final int blocksPerTickFinal = plugin.getConfig().getInt("blast.region.blocks-per-tick", 5000);

            List<BlastSavedBlock> list = new ArrayList<>(map.getSavedBlocks());
            final int totalBlocks = list.size();

            sender.sendMessage("§e[BLAST] Regenerating '" + name + "' (" + totalBlocks + " blocks) ...");

            new BukkitRunnable() {
                int i = 0;

                @Override
                public void run() {
                    if (i >= totalBlocks) {
                        sender.sendMessage("§a[BLAST] Regeneration complete for '" + name + "'.");
                        cancel();
                        return;
                    }

                    int placed = 0;

                    while (i < totalBlocks && placed < blocksPerTickFinal) {
                        BlastSavedBlock sb = list.get(i);

                        int bx = paste.getBlockX() + sb.dx;
                        int by = paste.getBlockY() + sb.dy;
                        int bz = paste.getBlockZ() + sb.dz;

                        Material mat = Material.matchMaterial(sb.material);
                        if (mat != null) {
                            Block block = paste.getWorld().getBlockAt(bx, by, bz);
                            block.setType(mat, false);
                        }

                        i++;
                        placed++;
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);

            return true;
        }

        sender.sendMessage("§cUnknown subcommand.");
        sendUsage(sender);
        return true;
    }

    private BlastMap requireMap(CommandSender sender, BlastMinigameManager mgr, String name) {
        if (name == null || name.isBlank()) {
            sender.sendMessage("§c[BLAST] Map name required.");
            return null;
        }
        BlastMap map = mgr.getMapStore().getMap(name);
        if (map == null) {
            sender.sendMessage("§c[BLAST] Map not found: " + name);
            return null;
        }
        return map;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/blastmap list");
        sender.sendMessage("§e/blastmap create <name>");
        sender.sendMessage("§e/blastmap info <name>");
        sender.sendMessage("§e/blastmap setceiling <name> <y>");
        sender.sendMessage("§e/blastmap select <name>");
        sender.sendMessage("§e/blastmap saveregion <name>");
        sender.sendMessage("§e/blastmap setpaste <name>");
        sender.sendMessage("§e/blastmap setspawn <name> <red|green|yellow|blue> <1-4>");
        sender.sendMessage("§e/blastmap startspawn <name> <x> <y> <z>");
        sender.sendMessage("§e/blastmap regen <name>");
    }

    private String format(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(null)";
        return loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }

    private int countNonNull(List<Location> list) {
        if (list == null) return 0;
        int c = 0;
        for (Location l : list) if (l != null) c++;
        return c;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

        if (!sender.hasPermission("portalplugin.blast.maps")) return List.of();

        if (args.length == 1) {
            return partial(args[0], List.of("list", "create", "saveregion", "setpaste", "setspawn", "setceiling", "startspawn", "select", "regen", "info"));
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("create")) return List.of();
            BlastMinigameManager mgr = plugin.getBlastMinigameManager();
            if (mgr == null) return List.of();
            return partial(args[1], mgr.getMapStore().listMapNames());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setspawn")) {
            return partial(args[2], List.of("red", "green", "yellow", "blue"));
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("setspawn")) {
            return partial(args[3], List.of("1", "2", "3", "4"));
        }

        return List.of();
    }

    private List<String> partial(String token, List<String> options) {
        if (token == null) token = "";
        String t = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        }
        return out;
    }
}
