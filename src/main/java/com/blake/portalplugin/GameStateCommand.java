package com.blake.portalplugin.commands;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GameStateCommand implements CommandExecutor, TabCompleter {

    private final GameStateManager gameStateManager;

    public GameStateCommand(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /gamestate.");
            return true;
        }

        Player player = (Player) sender;

        // /gamestate  → show current state
        if (args.length == 0) {
            GameState current = gameStateManager.getGameState(player);
            sender.sendMessage("Your current game state is: " + (current != null ? current.name() : "NONE"));
            return true;
        }

        // /gamestate <state>
        GameState state = GameState.fromString(args[0]);
        if (state == null) {
            sender.sendMessage("Unknown game state. Use HUB, ARENA, or SPLEEF.");
            return true;
        }

        gameStateManager.setGameState(player, state);
        sender.sendMessage("Game state set to: " + state.name());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toUpperCase();
            for (GameState state : GameState.values()) {
                if (state.name().startsWith(prefix)) {
                    suggestions.add(state.name());
                }
            }
        }
        return suggestions;
    }
}
