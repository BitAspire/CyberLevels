package com.bitaspire.cyberlevels.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CLVTabComplete implements TabCompleter {

    private static final String PLAYER_PREFIX = "CyberLevels.player.";
    private static final String ADMIN_PREFIX = "CyberLevels.admin.";

    private static final Map<String, String> COMMAND_PERMISSIONS = new HashMap<>();

    static {
        COMMAND_PERMISSIONS.put("about", PLAYER_PREFIX + "about");
        COMMAND_PERMISSIONS.put("info", PLAYER_PREFIX + "info");
        COMMAND_PERMISSIONS.put("top", PLAYER_PREFIX + "top");
        COMMAND_PERMISSIONS.put("help", PLAYER_PREFIX + "help");

        COMMAND_PERMISSIONS.put("reload", ADMIN_PREFIX + "reload");
        COMMAND_PERMISSIONS.put("list", ADMIN_PREFIX + "list");
        COMMAND_PERMISSIONS.put("purge", ADMIN_PREFIX + "purge");

        COMMAND_PERMISSIONS.put("addExp", ADMIN_PREFIX + "exp.add");
        COMMAND_PERMISSIONS.put("setExp", ADMIN_PREFIX + "exp.set");
        COMMAND_PERMISSIONS.put("removeExp", ADMIN_PREFIX + "exp.remove");

        COMMAND_PERMISSIONS.put("addLevel", ADMIN_PREFIX + "levels.add");
        COMMAND_PERMISSIONS.put("setLevel", ADMIN_PREFIX + "levels.set");
        COMMAND_PERMISSIONS.put("removeLevel", ADMIN_PREFIX + "levels.remove");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> available = new ArrayList<>();
            COMMAND_PERMISSIONS.forEach((cmd, perm) -> {
                if (player.hasPermission(perm)) available.add(cmd);
            });
            return partialMatch(args[0], available);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info":
                    if (player.hasPermission(ADMIN_PREFIX + "list"))
                        return partialMatch(args[1], getPlayerNames());
                    break;

                case "purge":
                    if (player.hasPermission(ADMIN_PREFIX + "purge"))
                        return partialMatch(args[1], getPlayerNames());
                    break;

                case "addexp": case "setexp": case "removeexp":
                    return partialMatch(args[1], Arrays.asList("<amount>", "5", "100", "250", "1000"));

                case "addlevel": case "setlevel": case "removelevel":
                    return partialMatch(args[1], Arrays.asList("<amount>", "1", "2", "5"));
            }
        }

        if (args.length == 3 &&
                Arrays.asList("addexp", "setexp", "removeexp", "addlevel", "setlevel", "removelevel")
                        .contains(args[0].toLowerCase()))
        {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("[<player>]");
            suggestions.addAll(getPlayerNames());
            return partialMatch(args[2], suggestions);
        }

        return Collections.emptyList();
    }

    private List<String> getPlayerNames() {
        List<String> players = new ArrayList<>();

        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            String name = p.getName();
            if (name != null) players.add(name);
        }

        return players;
    }

    private List<String> partialMatch(String input, List<String> options) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(input, options, matches);
        Collections.sort(matches);
        return matches;
    }
}
