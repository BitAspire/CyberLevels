package com.bitaspire.cyberlevels.command;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.cache.Lang;
import com.bitaspire.cyberlevels.level.LevelSystem;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.bitaspire.libs.common.util.ReplaceUtils;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the main {@code /clv} command tree.
 *
 * <p>This executor is responsible for player-facing informational commands, administrative EXP and
 * level mutations, reloads, leaderboard output, and console-compatible fallbacks. It also bridges
 * configurable language messages to both players and the console so command feedback stays aligned
 * with {@code lang.yml}.
 */
public class CLVCommand implements CommandExecutor {

    private final CyberLevels main;
    private final List<String> consoleCmds;

    /**
     * Creates the command executor bound to the current plugin runtime.
     *
     * @param main owning plugin instance
     */
    public CLVCommand(CyberLevels main) {
        this.main = main;
        this.consoleCmds = Arrays.asList(
            "about",
            "reload",
            "addexp",
            "setexp",
            "removeexp",
            "addlevel",
            "setlevel",
            "removelevel",
            "purge"
        );
    }

    /**
     * Executes the {@code /clv} command and its subcommands.
     *
     * <p>The method supports both player and console senders, validates permissions, resolves the
     * target player when needed, applies EXP or level changes, and renders feedback using the
     * configured language cache.
     *
     * @param sender command sender invoking the command
     * @param cmd Bukkit command metadata
     * @param label alias used to invoke the command
     * @param args raw subcommand arguments
     * @return always {@code true} because the command handles its own usage feedback
     */
    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command cmd,
        @NotNull String label,
        String[] args
    ) {
        Player player = (sender instanceof Player) ? (Player) sender : null;

        if (
            player == null &&
            (args.length == 0 || !consoleCmds.contains(args[0].toLowerCase()))
        ) {
            main.logger("&cConsole cannot use this command!");
            return true;
        }

        if (args.length == 0) return sendLevelInfo(player);

        String sub = args[0].toLowerCase();
        if (args.length == 1) {
            switch (sub) {
                case "about":
                    if (isRestricted(player, "player.about")) return true;
                    if (player == null) {
                        main.logger(
                            " &d&lCyber&f&lLevels &fv" +
                            main.getDescription().getVersion() +
                            " &7(&7&nhttps://bit.ly/2YSlqYq&7).",
                            " &fDeveloped by &d" + main.getAuthors() + "&f.",
                            " A leveling system plugin with MySQL support and custom events."
                        );
                        return true;
                    }
                    return main.createSender(player).send(
                        " &d&lCyber&f&lLevels &fv" +
                        main.getDescription().getVersion() +
                        " &7(&7&nhttps://bit.ly/2YSlqYq&7).",
                        " &fDeveloped by &d" + main.getAuthors() + "&f.",
                        " A leveling system plugin with MySQL support and custom events."
                    );
                case "reload":
                    if (isRestricted(player, "admin.reload")) return true;

                    sendLangMessage(sender, player, Lang::getReloading);
                    main.reloadPlugin();
                    return sendLangMessage(sender, player, Lang::getReloaded);
                case "info":
                    return sendLevelInfo(player);
                case "top":
                    if (isRestricted(player, "player.top")) return true;

                    sendLangMessage(sender, player, Lang::getTopHeader);
                    int i = 1;
                    for (LevelUser<?> user : main
                        .levelSystem()
                        .getLeaderboard()
                        .getTopTenPlayers()) {
                        sendLangMessage(
                            sender,
                            player,
                            Lang::getTopContent,
                            new String[] { "position", "player", "level", "exp" },
                            i++,
                            user.getName(),
                            user.getLevel(),
                            user.getExp()
                        );
                    }
                    return sendLangMessage(sender, player, Lang::getTopFooter);
            }
        }

        if (args.length == 2 && sub.equals("purge")) {
            LevelUser<?> target = main.userManager().getUser(args[1]);
            if (target != null) {
                main.userManager().removeUser(target.getUuid());
                main.levelSystem().getLeaderboard().update();
                return sendLangMessage(
                    sender,
                    player,
                    Lang::getPurgePlayer,
                        args[1]
                );
            }

            return isRestricted(player, "admin.info") || sendLevelInfo(player);
        }

        if (args.length == 2 && sub.equals("info")) {
            if (isRestricted(player, "admin.info")) return true;

            LevelUser<?> target = main.userManager().getUser(args[1]);
            if (target == null) {
                return sendLangMessage(
                    sender,
                    player,
                    Lang::getPlayerNotFound,
                        args[1]
                );
            }

            return sendLevelInfo(player, target);
        }

        if (args.length >= 2) {
            final String targetName = args.length >= 3 ? args[2] : null;

            LevelUser<?> user;
            if (targetName != null) {
                user = resolveUserByName(targetName);
                if (user == null) {
                    return sendLangMessage(
                        sender,
                        player,
                        Lang::getPlayerNotFound,
                            targetName
                    );
                }
            } else {
                if (player == null) {
                    main.logger(
                        "&cConsole must specify a player name for this command."
                    );
                    return true;
                }
                user = main.userManager().getUser(player);
                if (user == null) {
                    return sendLangMessage(
                        sender,
                        player,
                        Lang::getPlayerNotFound,
                            player.getName()
                    );
                }
            }

            String value = args[1];
            switch (sub) {
                case "addexp":
                    return handleExp(
                        sender,
                        player,
                        user,
                        value,
                        "exp.add",
                        true,
                        ExpAction.ADD
                    );
                case "setexp":
                    return handleExp(
                        sender,
                        player,
                        user,
                        value,
                        "exp.set",
                        true,
                        ExpAction.SET
                    );
                case "removeexp":
                    return handleExp(
                        sender,
                        player,
                        user,
                        value,
                        "exp.remove",
                        false,
                        ExpAction.REMOVE
                    );
                case "addlevel":
                    return handleLevel(
                        sender,
                        player,
                        user,
                        value,
                        "level.add",
                        LevelAction.ADD
                    );
                case "setlevel":
                    return handleLevel(
                        sender,
                        player,
                        user,
                        value,
                        "level.set",
                        LevelAction.SET
                    );
                case "removelevel":
                    return handleLevel(
                        sender,
                        player,
                        user,
                        value,
                        "level.remove",
                        LevelAction.REMOVE
                    );
            }
        }

        if (player != null) {
            if (player.hasPermission("CyberLevels.admin.help")) {
                return main.cache().lang().sendMessage(player, Lang::getHelpAdmin);
            }

            if (player.hasPermission("CyberLevels.player.help")) {
                return main
                    .cache()
                    .lang()
                    .sendMessage(player, Lang::getHelpPlayer);
            }
        }

        return sendLangMessage(sender, player, Lang::getNoPermission);
    }

    private boolean sendLangMessage(
        CommandSender cmdSender,
        Player player,
        Function<Lang, List<String>> langFn
    ) {
        if (player != null) {
            return main.cache().lang().sendMessage(player, langFn);
        }

        List<String> lines = langFn.apply(main.cache().lang());
        if (lines == null || lines.isEmpty()) return true;

        for (String line : lines) {
            String out = stripConsoleChannels(line);
            if (!out.isEmpty()) {
                cmdSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&', out)
                );
            }
        }
        return true;
    }

    private boolean sendLangMessage(
        CommandSender cmdSender,
        Player player,
        Function<Lang, List<String>> langFn,
        Object value
    ) {
        return sendLangMessage(
            cmdSender,
            player,
            langFn,
            new String[] {"player"},
            value
        );
    }

    private boolean sendLangMessage(
        CommandSender cmdSender,
        Player player,
        Function<Lang, List<String>> langFn,
        String[] keys,
        Object... values
    ) {
        if (player != null) {
            return main.cache().lang().sendMessage(player, langFn, keys, values);
        }

        List<String> lines = langFn.apply(main.cache().lang());
        if (lines == null || lines.isEmpty()) return true;

        Map<String, String> placeholders = new LinkedHashMap<>();
        if (keys != null && values != null && keys.length == values.length) {
            for (int i = 0; i < keys.length; i++) {
                placeholders.put(
                    '{' + keys[i] + '}',
                    String.valueOf(values[i])
                );
            }
        }

        for (String line : lines) {
            String out = ReplaceUtils.replaceEach(placeholders, line);
            out = stripConsoleChannels(out);
            if (!out.isEmpty()) {
                cmdSender.sendMessage(
                    ChatColor.translateAlternateColorCodes('&', out)
                );
            }
        }
        return true;
    }

    private static String stripConsoleChannels(String line) {
        if (line == null) return "";
        String stripped = line.replaceFirst("^\\[C]\\s*", "");
        return stripped.replace("[actionbar]", "").replace("[action-bar]", "").trim();
    }

    private LevelUser<?> resolveUserByName(String name) {
        if (name == null || name.trim().isEmpty()) return null;

        LevelUser<?> user = main.userManager().getUser(name);
        if (user != null) return user;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return main.userManager().getUser(online);

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore() && !offline.isOnline()) return null;

        return main.userManager().getUser(offline.getUniqueId());
    }

    private boolean sendLevelInfo(Player player) {
        LevelUser<?> user = main.userManager().getUser(player);
        LevelSystem<?> system = main.levelSystem();

        return main.cache().lang().sendMessage(
            player,
            Lang::getLevelInfo,
            new String[] {
                "player",
                "level",
                "maxLevel",
                "playerEXP",
                "requiredEXP",
                "percent",
                "progressBar",
            },
            user.getName(),
            user.getLevel(),
            main.cache().levels().getMaxLevel(),
            system.formatNumber(user.getExp()),
            system.formatNumber(user.getRequiredExp()),
            user.getPercent(),
            user.getProgressBar()
        );
    }

    private boolean sendLevelInfo(Player viewer, LevelUser<?> target) {
        LevelSystem<?> system = main.levelSystem();

        return main.cache().lang().sendMessage(
            viewer,
            Lang::getLevelInfo,
            new String[] {
                "player",
                "level",
                "maxLevel",
                "playerEXP",
                "requiredEXP",
                "percent",
                "progressBar",
            },
            target.getName(),
            target.getLevel(),
            main.cache().levels().getMaxLevel(),
            system.formatNumber(target.getExp()),
            system.formatNumber(target.getRequiredExp()),
            target.getPercent(),
            target.getProgressBar()
        );
    }

    private boolean handleExp(
        CommandSender sender,
        Player player,
        LevelUser<?> user,
        String arg,
        String perm,
        boolean allowMultiplier,
        ExpAction action
    ) {
        perm = "admin.levels." + perm;

        if (isRestricted(player, perm) || notDouble(sender, player, arg)) {
            return true;
        }

        double value;
        try {
            value = Math.abs(Double.parseDouble(arg));
        } catch (Exception ignored) {
            return true;
        }

        switch (action) {
            case ADD:
                user.addExp(
                    value,
                    main.cache().config().isMultiplierCommands()
                );
                break;
            case SET:
                user.setExp(value, allowMultiplier, true, true);
                break;
            case REMOVE:
                user.removeExp(value);
                break;
        }

        LevelSystem<?> system = main.levelSystem();

        return sendLangMessage(
            sender,
            player,
            action.getMessage(),
            new String[] { "player", action.getPlaceholder(), "level", "playerEXP" },
            user.getName(),
            arg,
            user.getLevel(),
            system.formatNumber(user.getExp())
        );
    }

    private boolean handleLevel(
        CommandSender sender,
        Player player,
        LevelUser<?> user,
        String arg,
        String perm,
        LevelAction action
    ) {
        if (isRestricted(player, perm) || notLong(sender, player, arg)) {
            return true;
        }

        long value;
        try {
            value = Math.abs(Long.parseLong(arg));
        } catch (Exception ignored) {
            return true;
        }

        switch (action) {
            case ADD:
                user.addLevel(value);
                break;
            case SET:
                user.setLevel(value, true);
                break;
            case REMOVE:
                user.removeLevel(value);
                break;
        }

        LevelSystem<?> system = main.levelSystem();

        return sendLangMessage(
            sender,
            player,
            action.getMessage(),
            new String[] { "player", action.getPlaceholder(), "level", "playerEXP" },
            user.getName(),
            arg,
            user.getLevel(),
            system.formatNumber(user.getExp())
        );
    }

    private boolean isRestricted(Player player, String permissionKey) {
        return (
            player != null &&
            (!player.hasPermission("CyberLevels." + permissionKey) &&
                main.cache().lang().sendMessage(player, Lang::getNoPermission))
        );
    }

    private boolean notLong(CommandSender sender, Player player, String arg) {
        try {
            Long.parseLong(arg);
            return false;
        } catch (Exception e) {
            return sendLangMessage(sender, player, Lang::getNotNumber);
        }
    }

    private boolean notDouble(CommandSender sender, Player player, String arg) {
        try {
            Double.parseDouble(arg);
            return false;
        } catch (Exception e) {
            return sendLangMessage(sender, player, Lang::getNotNumber);
        }
    }

    private enum ExpAction {
        ADD(Lang::getAddedExp, "addedEXP"),
        SET(Lang::getSetExp, "setEXP"),
        REMOVE(Lang::getRemovedExp, "removedEXP");

        private final Function<Lang, List<String>> lang;

        @Getter
        private final String placeholder;

        ExpAction(Function<Lang, List<String>> lang, String placeholder) {
            this.lang = lang;
            this.placeholder = placeholder;
        }

        public Function<Lang, List<String>> getMessage() {
            return lang;
        }
    }

    private enum LevelAction {
        ADD(Lang::getAddedLevels, "addedLevels"),
        SET(Lang::getSetLevel, "setLevel"),
        REMOVE(Lang::getRemovedLevels, "removedLevels");

        private final Function<Lang, List<String>> lang;

        @Getter
        private final String placeholder;

        LevelAction(Function<Lang, List<String>> lang, String placeholder) {
            this.lang = lang;
            this.placeholder = placeholder;
        }

        public Function<Lang, List<String>> getMessage() {
            return lang;
        }
    }
}
