package com.bitaspire.cyberlevels.command;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.cache.Lang;
import com.bitaspire.cyberlevels.user.LevelUser;
import lombok.Getter;
import me.croabeast.beanslib.message.MessageSender;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class CLVCommand implements CommandExecutor {

    private final CyberLevels main;
    private final List<String> consoleCmds;

    public CLVCommand(CyberLevels main) {
        this.main = main;
        this.consoleCmds = Arrays.asList("about", "reload", "addexp", "setexp", "removeexp", "addlevel", "setlevel", "removelevel", "purge");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        Player player = (sender instanceof Player) ? (Player) sender : null;

        // Console restrictions
        if (player == null && (args.length == 0 || !consoleCmds.contains(args[0].toLowerCase()))) {
            main.logger("&cConsole cannot use this command!");
            return true;
        }

        if (args.length == 0) return sendLevelInfo(player);

        String sub = args[0].toLowerCase();
        if (args.length == 1) {
            switch (sub) {
                case "about":
                    return isRestricted(player, "player.about") || new MessageSender(player).send(
                            " &d&lCyber&f&lLevels &fv" + main.getDescription().getVersion() + " &7(&7&nhttps://bit.ly/2YSlqYq&7).",
                            " &fDeveloped by &d" + main.getAuthors() + "&f.",
                            " A leveling system plugin with MySQL support and custom events."
                    );

                case "reload":
                    if (isRestricted(player, "admin.reload")) return true;

                    main.cache().lang().sendMessage(player, Lang::getReloading);
                    main.onDisable();
                    main.reloadPlugin();

                    return main.cache().lang().sendMessage(player, Lang::getReloaded);

                case "info": return sendLevelInfo(player);

                case "top":
                    if (isRestricted(player, "player.top")) return true;

                    main.cache().lang().sendMessage(player, Lang::getTopHeader);
                    int i = 1;

                    for (LevelUser<?> user : main.levelSystem().getLeaderboard().getTopTenPlayers()) {
                        main.cache().lang().sendMessage(
                                user.getPlayer(), Lang::getTopContent,
                                new String[]{"position", "player", "level", "exp"},
                                i++,
                                user.getPlayer().getName(), user.getLevel(), user.getExp()
                        );
                    }

                    main.cache().lang().sendMessage(player, Lang::getTopFooter);
                    return true;
            }
        }

        if (args.length == 2 && sub.equals("purge")) {
            Player target = getPlayer(args[1]);
            if (target != null) {
                main.userManager().removeUser(target.getUniqueId());
                return main.cache().lang().sendMessage(player, Lang::getPurgePlayer, "player", args[1]);
            }

            return isRestricted(player, "admin.info") || sendLevelInfo(player);
        }

        if (args.length == 2 && sub.equals("info")) {
            Player target = getPlayer(args[1]);
            return target != null ?
                    main.cache().lang().sendMessage(player, Lang::getPlayerOffline, "player", args[1]) :
                    (isRestricted(player, "admin.info") || sendLevelInfo(player));
        }

        if (args.length >= 2) {
            Player target = (args.length == 3) ? getPlayer(args[2]) : player;
            if (target == null) {
                main.cache().lang().sendMessage(player, Lang::getPlayerOffline, "player", args[2]);
                return true;
            }

            LevelUser<?> user = main.userManager().getUser(target);
            String value = args[1];

            switch (sub) {
                case "addexp":
                    return handleExp(player, user, value, "admin.levels.exp.add", true, ExpAction.ADD);

                case "setexp":
                    return handleExp(player, user, value, "admin.levels.exp.set", true, ExpAction.SET);

                case "removeexp":
                    return handleExp(player, user, value, "admin.levels.exp.remove", false, ExpAction.REMOVE);

                case "addlevel":
                    return handleLevel(player, user, value, "admin.levels.level.add", LevelAction.ADD);

                case "setlevel":
                    return handleLevel(player, user, value, "admin.levels.level.set", LevelAction.SET);

                case "removelevel":
                    return handleLevel(player, user, value, "admin.levels.level.remove", LevelAction.REMOVE);
            }
        }

        if (player != null) {
            if (player.hasPermission("CyberLevels.admin.help"))
                return main.cache().lang().sendMessage(player, Lang::getHelpAdmin);

            if (player.hasPermission("CyberLevels.player.help"))
                return main.cache().lang().sendMessage(player, Lang::getHelpPlayer);
        }

        return main.cache().lang().sendMessage(player, Lang::getNoPermission);
    }

    private boolean sendLevelInfo(Player player) {
        LevelUser<?> user = main.userManager().getUser(player);
        return main.cache().lang().sendMessage(
                player, Lang::getLevelInfo,
                new String[]{"player", "level", "maxLevel", "playerEXP", "requiredEXP", "percent", "progressBar"},
                user.getPlayer().getName(),
                user.getLevel(),
                user.getMaxLevel(),
                user.getExp(),
                user.getRoundedRequiredExp(),
                user.getPercent(),
                user.getProgressBar()
        );
    }

    private boolean handleExp(Player player, LevelUser<?> user, String arg, String perm, boolean allowMultiplier, ExpAction action) {
        if (isRestricted(player, perm) || notDouble(player, arg)) return true;
        double value = Math.abs(Double.parseDouble(arg));

        switch (action) {
            case ADD:
                user.addExp(value + "", main.cache().config().isMultiplierCommands());
                break;
            case SET:
                user.setExp(value + "", allowMultiplier, true, true);
                break;
            case REMOVE:
                user.removeExp(value + "");
                break;
        }

        return main.cache().lang().sendMessage(
                player, action.getMessage(),
                new String[] {action.getPlaceholder(), "level", "playerEXP"},
                arg, user.getLevel(), user.getRoundedExp()
        );
    }

    private boolean handleLevel(Player player, LevelUser<?> user, String arg, String perm, LevelAction action) {
        if (isRestricted(player, perm) || notLong(player, arg)) return true;
        long value = Math.abs(Long.parseLong(arg));

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

        return main.cache().lang().sendMessage(
                player, action.getMessage(),
                new String[] {action.getPlaceholder(), "level", "playerEXP"},
                arg, user.getLevel(), user.getRoundedExp()
        );
    }

    private boolean isRestricted(Player player, String permissionKey) {
        return player != null && (!player.hasPermission("CyberLevels." + permissionKey) &&
                main.cache().lang().sendMessage(player, Lang::getNoPermission));
    }

    private Player getPlayer(String player) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(player))
                .findFirst()
                .orElse(null);
    }

    private boolean notLong(Player player, String arg) {
        try {
            Long.parseLong(arg);
            return false;
        } catch (Exception e) {
            return main.cache().lang().sendMessage(player, Lang::getNotNumber);
        }
    }

    private boolean notDouble(Player player, String arg) {
        try {
            Double.parseDouble(arg);
            return false;
        } catch (Exception e) {
            return main.cache().lang().sendMessage(player, Lang::getNotNumber);
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
