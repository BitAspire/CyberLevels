package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.Message;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.croabeast.file.Configurable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

@Getter
public class Lang {

    private CLVFile file;

    private String prefix = "&d&lCyber&f&lLevels &8»&r";
    private List<String> noPermission = Collections.singletonList("&cYou don't have permission to do that!");

    private List<String> helpPlayer = Arrays.asList(
            "[C] &8&m―――――――&8<&d&l Cyber&f&lLevels &8>&8&m―――――――",
            "      &8➼ &d/clv about &fAbout the plugin.",
            "      &8➼ &d/clv help &fSee the help menu.",
            "      &8➼ &d/clv info &fSee level progress.",
            "[C] &8&m――――――――――――――――――――――――――――――――"
    );

    private List<String> helpAdmin = Arrays.asList(
            "[C] &8&m―――――――&8<&d&l Cyber&f&lLevels &8>&8&m―――――――",
            "      &8➼ &d/clv about &fAbout the plugin.",
            "      &8➼ &d/clv help &fSee the help menu.",
            "      &8➼ &d/clv reload &fReload the plugin.",
            "      &8➼ &d/clv info [<player>] &fSee level progress.",
            "      &8➼ &d/clv addEXP <amount> [<player>] &fIncrease a player's EXP balance.",
            "      &8➼ &d/clv setEXP <amount> [<player>] &fSet a player's EXP balance.",
            "      &8➼ &d/clv removeEXP <amount> [<player>] &fDecrease a player's EXP balance.",
            "      &8➼ &d/clv addLevel <amount> [<player>] &fIncrease a player's level.",
            "      &8➼ &d/clv setLevel <amount> [<player>] &fSet a player's level.",
            "      &8➼ &d/clv removeLevel <amount> [<player>] &fDecrease a player's level.",
            "[C] &8&m――――――――――――――――――――――――――――――――"
    );

    private String progressBar = "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    private String progressCompleteColor = "&d";
    private String progressIncompleteColor = "&7";
    private String progressEndColor = "&7";

    private List<String> reloading = Collections.singletonList("&7Reloading...");
    private List<String> reloaded = Collections.singletonList("&aReloaded!");
    private List<String> autoSave = Collections.singletonList("&aSuccessfully auto-saved all player data (&7{ms}ms&a).");

    private List<String> addedExp = Collections.singletonList("&aAdded {addedEXP} to {player}'s experience. They are now level {level} with {playerEXP} experience.");
    private List<String> setExp = Collections.singletonList("&aSet {player}'s experience to {setEXP}. They are now level {level} with {playerEXP} experience.");
    private List<String> removedExp = Collections.singletonList("&aRemoved {removedEXP} from {player}'s experience. They are now level {level} with {playerEXP} experience.");

    private List<String> addedLevels = Collections.singletonList("&aAdded {addedLevels} to {player}'s level(s). They are now level {level} with {playerEXP} experience.");
    private List<String> setLevel = Collections.singletonList("&aSet {player}'s level to {setLevel}. They are now level {level} with {playerEXP} experience.");
    private List<String> removedLevels = Collections.singletonList("&aRemoved {removedLevels} from {player}'s level(s). They are now level {level} with {playerEXP} experience.");

    private List<String> playerNotFound = Collections.singletonList("&cThe player {player} is not found in the database!");
    private List<String> notNumber = Collections.singletonList("&cThat is not a number!");
    private List<String> purgePlayer = Collections.singletonList("&cThe player {player} was removed from CLV''s data.");

    private List<String> levelInfo = Arrays.asList(
            "[C] &8&m―――――――&8<&d&l Level&f&lStats &8>&8&m―――――――",
            "[C] &7Player: &f{player}",
            "[C] &7Level: &f{level}&7/&d{maxLevel}",
            "[C] &7EXP: &f{playerEXP}&7/&d{requiredEXP} &7[&f{percent}%&7]",
            "[C] &7[{progressBar}&7]",
            "[C] &8&m――――――――――――――――――――――――――――――――"
    );

    private List<String> gainedExp = Collections.singletonList("[actionbar] &d+{gainedEXP} EXP");
    private List<String> lostExp = Collections.singletonList("[actionbar] &c-{lostEXP} EXP");
    private List<String> gainedLevels = Collections.singletonList("[actionbar] &d+{gainedLevels} Level(s)");
    private List<String> lostLevels = Collections.singletonList("[actionbar] &c-{lostLevels} Level(s)");

    private List<String> topHeader = Collections.singletonList("[C] &8&m―――――――&8<&d&l Top &f&lPlayers &8>&8&m―――――――");
    private List<String> topContent = Collections.singletonList("&f[{position}] &d{player}&7: &7level: &f{level}&7, exp: &f{exp}");
    private List<String> topFooter = Collections.singletonList("[C] &8&m――――――――――――――――――――――――――――――――");

    @Getter(AccessLevel.NONE)
    private final CyberLevels main;

    @Accessors(fluent = true)
    private LeaderboardKeys leaderboardKeys = new LeaderboardKeys();

    Lang(CyberLevels main) {
        this.main = main;
        try {
            prefix = (file = new CLVFile(main, "lang")).get("messages.prefix", prefix);
            noPermission = Configurable.toStringList(file.getConfiguration(), "messages.no-permission", noPermission);

            helpPlayer = Configurable.toStringList(file.getConfiguration(), "messages.help-player", helpPlayer);
            helpAdmin = Configurable.toStringList(file.getConfiguration(), "messages.help-admin", helpAdmin);

            progressBar = file.get("messages.progress.bar", progressBar);
            progressCompleteColor = file.get("messages.progress.complete-color", progressCompleteColor);
            progressIncompleteColor = file.get("messages.progress.incomplete-color", progressIncompleteColor);
            progressEndColor = file.get("messages.progress.end-color", progressEndColor);

            reloading = Configurable.toStringList(file.getConfiguration(), "messages.reloading", reloading);
            reloaded = Configurable.toStringList(file.getConfiguration(), "messages.reloaded", reloaded);
            autoSave = Configurable.toStringList(file.getConfiguration(), "messages.auto-save", autoSave);

            addedExp = Configurable.toStringList(file.getConfiguration(), "messages.added-exp", addedExp);
            setExp = Configurable.toStringList(file.getConfiguration(), "messages.set-exp", setExp);
            removedExp = Configurable.toStringList(file.getConfiguration(), "messages.removed-exp", removedExp);

            addedLevels = Configurable.toStringList(file.getConfiguration(), "messages.added-levels", addedLevels);
            setLevel = Configurable.toStringList(file.getConfiguration(), "messages.set-level", setLevel);
            removedLevels = Configurable.toStringList(file.getConfiguration(), "messages.removed-levels", removedLevels);

            playerNotFound = Configurable.toStringList(file.getConfiguration(), "messages.player-not-found", playerNotFound);
            notNumber = Configurable.toStringList(file.getConfiguration(), "messages.not-number", notNumber);
            purgePlayer = Configurable.toStringList(file.getConfiguration(), "messages.purge-player", purgePlayer);

            levelInfo = Configurable.toStringList(file.getConfiguration(), "messages.level-info", levelInfo);

            gainedExp = Configurable.toStringList(file.getConfiguration(), "messages.gained-exp", gainedExp);
            lostExp = Configurable.toStringList(file.getConfiguration(), "messages.lost-exp", lostExp);
            gainedLevels = Configurable.toStringList(file.getConfiguration(), "messages.gained-levels", gainedLevels);
            lostLevels = Configurable.toStringList(file.getConfiguration(), "messages.lost-levels", lostLevels);

            topHeader = Configurable.toStringList(file.getConfiguration(), "messages.top-header", topHeader);
            topContent = Configurable.toStringList(file.getConfiguration(), "messages.top-content", topContent);
            topFooter = Configurable.toStringList(file.getConfiguration(), "messages.top-footer", topFooter);

            leaderboardKeys = new LeaderboardKeys(file.getSection("messages.leaderboard-placeholders"));
        }
        catch (IOException ignored) {}
    }

    public void update() {
        if (file != null) file.update();
    }

    public boolean sendMessage(Player player, Function<Lang, List<String>> function, String[] keys, Object... values) {
        return new Message().player(player).list(function).keys(keys).values(values).send();
    }

    public boolean sendMessage(Player player, Function<Lang, List<String>> function, String key, Object value) {
        return sendMessage(player, function, new String[] {key}, value.toString());
    }

    public boolean sendMessage(Player player, Function<Lang, List<String>> function) {
        return sendMessage(player, function, null);
    }

    @Getter
    public static class LeaderboardKeys {

        private String noPlayerName = "&c-";
        private String noPlayerLevel = "&c-";
        private String noPlayerExp = "&c-";
        private String loadingName = "&6loading...";
        private String loadingLevel = "&6-";
        private String loadingExp = "&6-";

        LeaderboardKeys() {}

        LeaderboardKeys(ConfigurationSection section) {
            if (section == null) return;

            noPlayerName = section.getString("no-player-name", noPlayerName);
            noPlayerLevel = section.getString("no-player-level", noPlayerLevel);
            noPlayerExp = section.getString("no-player-exp", noPlayerExp);
            loadingName = section.getString("loading-name", loadingName);
            loadingLevel = section.getString("loading-level", loadingLevel);
            loadingExp = section.getString("loading-exp", loadingExp);
        }
    }
}
