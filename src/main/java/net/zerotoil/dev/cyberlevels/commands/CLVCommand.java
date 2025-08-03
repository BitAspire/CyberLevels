package net.zerotoil.dev.cyberlevels.commands;

import net.zerotoil.dev.cyberlevels.CyberLevels;
import net.zerotoil.dev.cyberlevels.objects.leaderboard.LeaderboardPlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class CLVCommand implements CommandExecutor {

    private final CyberLevels main;
    private final List<String> consoleCmds;

    public CLVCommand(CyberLevels main) {
        this.main = main;
        main.getCommand("clv").setExecutor(this);
        consoleCmds = Arrays.asList("about", "reload", "addexp", "setexp", "removeexp", "addlevel", "setlevel", "removelevel");
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        Player player;
        String uuid;

        // console check
        if (!(sender instanceof Player)) {
            if (args.length == 0 || !consoleCmds.contains(args[0].toLowerCase())) {
                main.logger("&cConsole cannot use this command!");
                return true;
            }
            player = null;
            uuid = null;
        } else {
            player = (Player) sender;
            uuid = player.getUniqueId().toString();
        }

        if (args.length == 0) {
            if (noPlayerPerm(player, "player.info")) return true;
            main.core().sendMessage(player, "level-info");
            return true;
        }

        if (args.length == 1) {

            switch (args[0].toLowerCase()) {
                case "about":

                    if (noPlayerPerm(player, "player.about")) return true;
                    main.getLangUtils().sendMixed(player, " &d&lCyber&f&lLevels &fv" + main.getDescription().getVersion() + " &7(&7&nhttps://bit.ly/2YSlqYq&7).");
                    main.getLangUtils().sendMixed(player, " &fDeveloped by &d" + main.getAuthors() + "&f.");
                    main.getLangUtils().sendMixed(player, " A leveling system plugin with MySQL support and custom events.");
                    return true;

                case "reload":
                    if (noPlayerPerm(player, "admin.reload")) return true;
                    main.core().sendMessage(player, "reloading");

                    // unload
                    main.onDisable();

                    // load
                    main.reloadPlugin();

                    main.core().sendMessage(player, "reloaded");
                    return true;

                case "info":
                    if (noPlayerPerm(player, "player.info")) return true;
                    main.core().sendMessage(player, "level-info");
                    return true;

                case "top":
                    if (noPlayerPerm(player, "player.top")) return true;
                    main.core().sendMessage(player, "top-header");
                    int i = 1;
                    for (final LeaderboardPlayer lPlayer : main.getLevelCache().getLeaderboard().getTopTenPlayers()) {
                        final OfflinePlayer p = lPlayer.getPlayer();
                        if (p != null) {
                            main.core().sendMessage(player, "top-content",
                                    new String[]{"{position}", "{player}", "{level}", "{exp}"},
                                    i + "", p.getName(), lPlayer.getLevel() + "", main.getLevelUtils().roundStringDecimal(lPlayer.getExp())
                            );
                            i++;
                        }
                    }
                    main.core().sendMessage(player, "top-footer");
                    return true;

            }
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info":
                    Player target = getPlayer(args[1]);
                    if (target == null) {
                        main.core().sendMessage(player, "player-offline", new String[]{"{player}"}, args[1]);
                        return true;
                    }
                    if (noPlayerPerm(player, "admin.info")) return true;
                    main.core().sendMessage(player, "level-info");
                    return true;
            }
        }

        if (player == null && args.length != 3) {
            main.logger("&cYou need to specify a player!");
            return true;
        }
        if (args.length == 3 || args.length == 2) {

            Player target;
            if (args.length == 3) {
                target = getPlayer(args[2]);
                if (target == null) {
                    main.core().sendMessage(player, "player-offline", new String[]{"{player}"}, args[2]);
                    return true;
                }
            } else target = player;

            switch (args[0].toLowerCase()) {
                case "addexp":
                    if (noPlayerPerm(player, "admin.levels.exp.add")) return true;
                    if (notDouble(player, args[1])) return true;
                    main.getLevelCache().playerLevels().get(target).addExp(Math.abs(Double.parseDouble(args[1])), main.getLevelCache().doCommandMultiplier());
                    main.core().sendMessage(player, "added-exp", new String[]{"{addedEXP}"}, args[1]);
                    return true;

                case "setexp":
                    if (noPlayerPerm(player, "admin.levels.exp.set")) return true;
                    if (notDouble(player, args[1])) return true;
                    main.getLevelCache().playerLevels().get(target).setExp(Math.abs(Double.parseDouble(args[1])), true, true);
                    main.core().sendMessage(player, "set-exp", new String[]{"{setEXP}"}, args[1]);
                    return true;

                case "removeexp":
                    if (noPlayerPerm(player, "admin.levels.exp.remove")) return true;
                    if (notDouble(player, args[1])) return true;
                    main.getLevelCache().playerLevels().get(target).removeExp(Math.abs(Double.parseDouble(args[1])));
                    main.core().sendMessage(player, "removed-exp", new String[]{"{removedEXP}"}, args[1]);
                    return true;

                case "addlevel":
                    if (noPlayerPerm(player, "admin.levels.level.add")) return true;
                    if (notLong(player, args[1])) return true;
                    main.getLevelCache().playerLevels().get(target).addLevel(Math.abs(Long.parseLong(args[1])));
                    main.core().sendMessage(player, "added-levels", new String[]{"{addedLevels}"}, args[1]);
                    return true;

                case "setlevel":
                    if (noPlayerPerm(player, "admin.levels.level.set")) return true;
                    if (notLong(player, args[1])) return true;
                    main.getLevelCache().playerLevels().get(target).setLevel(Math.abs(Long.parseLong(args[1])), true);
                    main.core().sendMessage(player, "set-level", new String[]{"{setLevel}"}, args[1]);
                    return true;

                case "removelevel":
                    if (noPlayerPerm(player, "admin.levels.level.remove")) return true;
                    if (notLong(player, args[1])) return true;
                    main.getLevelCache().playerLevels().get(target).removeLevel(Math.abs(Long.parseLong(args[1])));
                    main.core().sendMessage(player, "removed-levels", new String[]{"{removedLevels}"}, args[1]);
                    return true;

            }

        }

        // final outcome, if command does not exist:
        if (player.hasPermission("CyberLevels.admin.help")) main.getLangUtils().sendHelp(player, true);
        else if (player.hasPermission("CyberLevels.player.help")) main.getLangUtils().sendHelp(player, false);
        else main.core().sendMessage(player, "no-permission");
        return true;


    }

    private boolean noPlayerPerm(Player player, String permissionKey) {
        if (player == null) return false;
        if (!player.hasPermission("CyberLevels." + permissionKey)) {
            main.core().sendMessage(player, "no-permission");
            return true;
        }
        return false;
    }

    private Player getPlayer(String player) {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().equalsIgnoreCase(player)) return p;
        return null;
    }

    private boolean notLong(Player player, String arg) {
        try {
            Long.parseLong(arg);
            return false;
        } catch (Exception e) {
            main.core().sendMessage(player, "not-number");
            return true;
        }
    }
    private boolean notDouble(Player player, String arg) {
        try {
            Double.parseDouble(arg);
            return false;
        } catch (Exception e) {
            main.core().sendMessage(player, "not-number");
            return true;
        }
    }

}
