package com.bitaspire.cyberlevels.command;

import com.bitaspire.cyberlevels.CyberLevels;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Tab completer for the {@code /clv} command tree.
 *
 * <p>The completer filters suggestions by permission, offers common numeric examples for mutation
 * commands, and can resolve either online-only or online-plus-offline player names depending on
 * the current configuration.
 */
@RequiredArgsConstructor
public class CLVTabComplete implements TabCompleter {

    private static final String PLAYER_PREFIX = "CyberLevels.player.";
    private static final String ADMIN_PREFIX = "CyberLevels.admin.";
    private static final long PLAYER_NAME_CACHE_MILLIS = 5000L;

    private static final List<String> EXP_AMOUNT_SUGGESTIONS = Collections.unmodifiableList(
        Arrays.asList("<amount>", "5", "100", "250", "1000")
    );
    private static final List<String> LEVEL_AMOUNT_SUGGESTIONS = Collections.unmodifiableList(
        Arrays.asList("<amount>", "1", "2", "5")
    );
    private static final Set<String> MUTATION_COMMANDS = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList("addexp", "setexp", "removeexp", "addlevel", "setlevel", "removelevel")
    ));
    private static final List<String> CONSOLE_COMMANDS = Collections.unmodifiableList(
        Arrays.asList("about", "reload", "addexp", "setexp", "removeexp", "addlevel", "setlevel", "removelevel", "purge")
    );
    private static final Map<String, String> COMMAND_PERMISSIONS = new LinkedHashMap<>();

    static {
        COMMAND_PERMISSIONS.put("about", PLAYER_PREFIX + "about");
        COMMAND_PERMISSIONS.put("info", PLAYER_PREFIX + "info");
        COMMAND_PERMISSIONS.put("top", PLAYER_PREFIX + "top");
        COMMAND_PERMISSIONS.put("help", PLAYER_PREFIX + "help");

        COMMAND_PERMISSIONS.put("reload", ADMIN_PREFIX + "reload");
        COMMAND_PERMISSIONS.put("list", ADMIN_PREFIX + "list");
        COMMAND_PERMISSIONS.put("purge", ADMIN_PREFIX + "purge");

        COMMAND_PERMISSIONS.put("addexp", ADMIN_PREFIX + "exp.add");
        COMMAND_PERMISSIONS.put("setexp", ADMIN_PREFIX + "exp.set");
        COMMAND_PERMISSIONS.put("removeexp", ADMIN_PREFIX + "exp.remove");

        COMMAND_PERMISSIONS.put("addlevel", ADMIN_PREFIX + "levels.add");
        COMMAND_PERMISSIONS.put("setlevel", ADMIN_PREFIX + "levels.set");
        COMMAND_PERMISSIONS.put("removelevel", ADMIN_PREFIX + "levels.remove");
    }

    private final CyberLevels main;
    private long playerNamesCachedAt = 0L;
    private boolean playerNamesCachedOfflineMode = false;
    private List<String> cachedPlayerNames = Collections.emptyList();

    /**
     * Produces context-aware tab completions for the CyberLevels command set.
     *
     * @param sender command sender requesting completions
     * @param command Bukkit command metadata
     * @param alias alias used to invoke the command
     * @param args current partial arguments
     * @return ordered list of suggested completions for the current argument position
     */
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;

        if (args.length == 1) {
            List<String> available = new ArrayList<>();
            if (player == null) {
                available.addAll(CONSOLE_COMMANDS);
            } else {
                COMMAND_PERMISSIONS.forEach((cmd, perm) -> {
                    if (player.hasPermission(perm)) available.add(cmd);
                });
            }
            return partialMatch(args[0], available);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info":
                    if (player != null && player.hasPermission(ADMIN_PREFIX + "list"))
                        return partialMatch(args[1], getPlayerNames());
                    break;

                case "purge":
                    if (player == null || player.hasPermission(ADMIN_PREFIX + "purge"))
                        return partialMatch(args[1], getPlayerNames());
                    break;

                case "addexp": case "setexp": case "removeexp":
                    return partialMatch(args[1], EXP_AMOUNT_SUGGESTIONS);

                case "addlevel": case "setlevel": case "removelevel":
                    return partialMatch(args[1], LEVEL_AMOUNT_SUGGESTIONS);
            }
        }

        if (args.length == 3 && MUTATION_COMMANDS.contains(args[0].toLowerCase(Locale.ENGLISH)))
        {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("[<player>]");
            suggestions.addAll(getPlayerNames());
            return partialMatch(args[2], suggestions);
        }

        return Collections.emptyList();
    }

    private List<String> getPlayerNames() {
        boolean offlineMode = main.cache().config().isTabCompleteLoadOfflineUsers();
        long now = System.currentTimeMillis();

        if (offlineMode == playerNamesCachedOfflineMode &&
            now - playerNamesCachedAt <= PLAYER_NAME_CACHE_MILLIS)
            return cachedPlayerNames;

        LinkedHashSet<String> players = new LinkedHashSet<>();

        if (offlineMode) {
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                String name = p.getName();
                if (name != null) players.add(name);
            }
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
        }

        List<String> snapshot = new ArrayList<>(players);
        snapshot.sort(String.CASE_INSENSITIVE_ORDER);
        cachedPlayerNames = snapshot;
        playerNamesCachedOfflineMode = offlineMode;
        playerNamesCachedAt = now;
        return cachedPlayerNames;
    }

    private List<String> partialMatch(String input, List<String> options) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(input, options, matches);
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }
}
