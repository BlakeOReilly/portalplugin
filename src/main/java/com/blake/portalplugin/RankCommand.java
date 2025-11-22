package com.blake.portalplugin.commands;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.ranks.Rank;
import com.blake.portalplugin.ranks.RankManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final RankManager rankManager;
    private final GameStateManager gameStateManager;

    public RankCommand(RankManager rankManager, GameStateManager gameStateManager) {
        this.rankManager = rankManager;
        this.gameStateManager = gameStateManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /rank.");
            return true;
        }

        // /rank  -> show current rank
        if (args.length == 0) {
            Rank current = rankManager.getRankOrDefault(player.getUniqueId());
            String display = (current == Rank.NONE ? "NO RANK" : current.name());
            player.sendMessage("Your rank: " + display);
            return true;
        }

        // /rank <rank>  -> set own rank (requires ADMIN gamestate)
        if (args.length == 1) {

            if (gameStateManager.getGameState(player) != GameState.ADMIN) {
                player.sendMessage("You must be in ADMIN gamestate to change your rank.");
                return true;
            }

            String arg = args[0];
            // allow "none" / "norank" to clear rank
            if (arg.equalsIgnoreCase("none") || arg.equalsIgnoreCase("norank")) {
                rankManager.setRank(player.getUniqueId(), Rank.NONE);
                player.sendMessage("Your rank has been cleared. You now have NO RANK.");
                return true;
            }

            Rank newRank = Rank.fromString(arg);
            if (newRank == null || newRank == Rank.NONE) {
                player.sendMessage("Unknown rank. Valid ranks: ALPHA, VALOR, MYTHIC, TRAINEE, MOD, SRMOD, ADMIN, OWNER, NONE");
                return true;
            }

            rankManager.setRank(player.getUniqueId(), newRank);
            player.sendMessage("Your rank has been set to: " + newRank.name());
            return true;
        }

        player.sendMessage("Usage: /rank [rank]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toUpperCase();

            // include NONE for clearing
            for (Rank r : Rank.values()) {
                if (r.name().startsWith(prefix)) {
                    suggestions.add(r.name());
                }
            }

            // extra aliases
            if ("NONE".startsWith(prefix)) {
                suggestions.add("NONE");
            }
        }

        return suggestions;
    }
}
