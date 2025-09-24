package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.configuration.ConfigurationSection;

@Getter
public class Config {

    private CLVFile file;

    @Accessors(fluent = true)
    private Database database = new Database();

    @Accessors(fluent = true)
    private boolean useBigDecimalSystem = false;

    private int roundingDigits = 2;
    private boolean roundingEnabled = true;
    @Accessors(fluent = true)
    private boolean roundEarnExp = true;

    private boolean expIntegerOnly = false;

    private boolean leaderboardEnabled = true;
    @Accessors(fluent = true)
    private boolean syncLeaderboardOnAutoSave = true,
            leaderboardInstantUpdate = false;

    private boolean autoSaveEnabled = true;
    private int autoSaveInterval = 300;

    @Accessors(fluent = true)
    private boolean preventDuplicateRewards = false,
            stackComboExp = true,
            addLevelRewards = false;

    private boolean multiplierCommands = false;
    private boolean multiplierEvents = true;

    @Accessors(fluent = true)
    private boolean autoUpdateConfig = true,
            autoUpdateLang = true,
            autoUpdateEarnExp = true;

    private boolean messagesOnAutoSave = true;
    private boolean messagesOnConsole = true;

    Config(CyberLevels main) {
        try {
            database = new Database((file = new CLVFile(main, "config")).getSection("config.mysql"));
            useBigDecimalSystem = file.get("config.use-big-decimal-system", false);

            roundingEnabled = file.get("config.round-evaluation.enabled", true);
            roundEarnExp = file.get("config.round-evaluation.round-earn-exp", true);
            roundingDigits = file.get("config.round-evaluation.digits", roundingDigits);

            expIntegerOnly = file.get("config.earn-exp.integer-only", false);

            leaderboardEnabled = file.get("config.leaderboard.enabled", true);
            syncLeaderboardOnAutoSave = file.get("config.leaderboard.sync-on-auto-save", true);
            leaderboardInstantUpdate = file.get("config.leaderboard.instant-update", false);

            addLevelRewards = file.get("config.add-level-reward", false);
            preventDuplicateRewards = file.get("config.prevent-duplicate-rewards", false);
            stackComboExp = file.get("config.stack-combo-exp", true);

            autoSaveEnabled = file.get("config.auto-save.enabled", true);
            autoSaveInterval = file.get("config.auto-save.interval", autoSaveInterval);

            multiplierCommands = file.get("config.multiplier.commands", false);
            multiplierEvents = file.get("config.multiplier.events", true);

            autoUpdateConfig = file.get("config.auto-update.config", true);
            autoUpdateLang = file.get("config.auto-update.lang", true);
            autoUpdateEarnExp = file.get("config.auto-update.earn-exp", true);

            messagesOnAutoSave = file.get("config.messages.auto-save", true);
            messagesOnConsole = file.get("config.messages.message-console", true);
        }
        catch (Exception ignored) {}
    }

    public void update() {
        if (file != null) file.update();
    }

    @Accessors(fluent = false)
    @Getter
    public static class Database {

        private boolean enabled = false, ssl = true;
        private String host = "localhost", port = "3306",
                database = "database",
                username = "username", password = "password",
                table = "levels", type = "MySQL",
                sqliteFile = "plugins/CyberLevels/data.db";

        Database(ConfigurationSection section) {
            if (section == null) return;

            enabled = section.getBoolean("enabled");
            ssl = section.getBoolean("ssl", true);

            host = section.getString("host", host);
            port = section.getString("port", port);
            database = section.getString("database", database);
            username = section.getString("username", username);
            password = section.getString("password", password);
            table = section.getString("table", table);

            sqliteFile = section.getString("sqlite-file", sqliteFile);
            type = section.getString("type", type);
        }

        Database() {}
    }
}
