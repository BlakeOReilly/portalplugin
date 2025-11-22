package com.blake.portalplugin.commands;

import com.blake.portalplugin.currency.CurrencyManager;
import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class CurrencyCommand implements CommandExecutor {

    private final CurrencyManager currencyManager;
    private final GameStateManager gameStateManager;

    public CurrencyCommand(CurrencyManager cm, GameStateManager gsm) {
        this.currencyManager = cm;
        this.gameStateManager = gsm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // -----------------------------------------------
        // /currency  (self view)
        // -----------------------------------------------
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /currency.");
                return true;
            }

            int coins = currencyManager.getCoins(player.getUniqueId());
            int gems = currencyManager.getGems(player.getUniqueId());

            player.sendMessage("§eYour Coins: §a" + coins);
            player.sendMessage("§eYour Gems: §b" + gems);

            return true;
        }

        // -----------------------------------------------
        // ADMIN SUBCOMMANDS REQUIRE ADMIN GAMESTATE
        // -----------------------------------------------
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only ADMIN players can modify currency.");
            return true;
        }

        if (gameStateManager.getGameState(p) != GameState.ADMIN) {
            p.sendMessage("§cYou must be in ADMIN gamestate to use this.");
            return true;
        }

        if (args.length < 4) {
            p.sendMessage("§cUsage:");
            p.sendMessage("/currency give <player> <coins|gems> <amount>");
            p.sendMessage("/currency set <player> <coins|gems> <amount>");
            p.sendMessage("/currency take <player> <coins|gems> <amount>");
            return true;
        }

        String sub = args[0].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String type = args[2].toLowerCase();
        int amount;

        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            p.sendMessage("§cAmount must be a number.");
            return true;
        }

        if (amount < 0) {
            p.sendMessage("§cAmount cannot be negative.");
            return true;
        }

        // -----------------------------------------------
        // PERFORM ACTION
        // -----------------------------------------------
        switch (sub) {

            case "give" -> {
                if (type.equals("coins"))
                    currencyManager.addCoins(target.getUniqueId(), amount);
                else if (type.equals("gems"))
                    currencyManager.addGems(target.getUniqueId(), amount);
                else {
                    p.sendMessage("§cInvalid type. Use coins or gems.");
                    return true;
                }
                p.sendMessage("§aGave " + amount + " " + type + " to " + target.getName());
            }

            case "set" -> {
                if (type.equals("coins"))
                    currencyManager.setCoins(target.getUniqueId(), amount);
                else if (type.equals("gems"))
                    currencyManager.setGems(target.getUniqueId(), amount);
                else {
                    p.sendMessage("§cInvalid type. Use coins or gems.");
                    return true;
                }
                p.sendMessage("§aSet " + target.getName() + "'s " + type + " to " + amount);
            }

            case "take" -> {
                if (type.equals("coins"))
                    currencyManager.removeCoins(target.getUniqueId(), amount);
                else if (type.equals("gems"))
                    currencyManager.removeGems(target.getUniqueId(), amount);
                else {
                    p.sendMessage("§cInvalid type. Use coins or gems.");
                    return true;
                }
                p.sendMessage("§aRemoved " + amount + " " + type + " from " + target.getName());
            }

            default -> p.sendMessage("§cUnknown action. Use give/set/take.");
        }

        return true;
    }
}
