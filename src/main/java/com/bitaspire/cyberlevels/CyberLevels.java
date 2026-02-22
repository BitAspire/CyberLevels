package com.bitaspire.cyberlevels;

import com.bitaspire.common.util.ServerInfoUtils;
import com.bitaspire.cybercore.CoreSettings;
import com.bitaspire.cybercore.CyberCore;
import com.bitaspire.cyberlevels.cache.Cache;
import com.bitaspire.cyberlevels.command.CLVCommand;
import com.bitaspire.cyberlevels.command.CLVTabComplete;
import com.bitaspire.cyberlevels.hook.HookManager;
import com.bitaspire.cyberlevels.level.LevelSystem;
import com.bitaspire.cyberlevels.listener.Listeners;
import com.bitaspire.cyberlevels.user.Database;
import com.bitaspire.cyberlevels.user.UserManager;
import com.bitaspire.takion.TakionLib;
import com.bitaspire.takion.message.MessageSender;
import com.bitaspire.scheduler.GlobalScheduler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@Accessors(fluent = true)
@Getter
public final class CyberLevels extends JavaPlugin {

    @Accessors(fluent = true)
    @Getter
    static CyberLevels instance;

    GlobalScheduler scheduler;

    CyberCore core;
    Cache cache;

    @Getter(AccessLevel.NONE)
    Listeners listeners;

    LevelSystem<?> levelSystem;

    UserManager<?> userManager;
    @Getter(AccessLevel.NONE)
    Database<?> database;

    @Getter(AccessLevel.NONE)
    HookManager hookManager;

    @Override
    public void onEnable() {
        if (serverVersion() < 16) {
            DependencyLoader loader = DependencyLoader.BUKKIT_LOADER;
            loader.load("ch.obermuhlner", "big-math", "2.3.2", true);
            loader.load("org.slf4j", "slf4j-api", "1.7.36", true);
            loader.load("com.zaxxer", "HikariCP", "4.0.3", true);
            loader.load("com.mysql", "mysql-connector-j", "8.0.33", true);
            loader.load("org.xerial", "sqlite-jdbc", "3.51.1.0", true);
            loader.load("org.postgresql", "postgresql", "42.7.8", true);
            loader.load("org.apache.commons", "commons-lang3", "3.18.0", true);
        }

        instance = this;
        scheduler = GlobalScheduler.getScheduler(this);
        core = new CyberCore(this);

        CoreSettings settings = core.getSettings();
        settings.setBootColor('d');
        settings.setBootLogo(
                "&d╭━━━╮&7╱╱╱&d╭╮&7╱╱╱╱╱╱&d╭╮&7╱╱╱╱╱╱╱╱╱╱╱&d╭╮",
                "&d┃╭━╮┃&7╱╱╱&d┃┃&7╱╱╱╱╱╱&d┃┃&7╱╱╱╱╱╱╱╱╱╱╱&d┃┃",
                "&d┃┃&7╱&d╰╋╮&7╱&d╭┫╰━┳━━┳━┫┃&7╱╱&d╭━━┳╮╭┳━━┫┃╭━━╮",
                "&d┃┃&7╱&d╭┫┃&7╱&d┃┃╭╮┃┃━┫╭┫┃&7╱&d╭┫┃━┫╰╯┃┃━┫┃┃━━┫",
                "&d┃╰━╯┃╰━╯┃╰╯┃┃━┫┃┃╰━╯┃┃━╋╮╭┫┃━┫╰╋━━┃",
                "&d╰━━━┻━╮╭┻━━┻━━┻╯╰━━━┻━━╯╰╯╰━━┻━┻━━╯",
                "&7╱╱╱╱&d╭━╯┃  &7Authors: &f" + getAuthors(),
                "&7╱╱╱╱&d╰━━╯  &7Version: &f" + this.getDescription().getVersion()
        );

        core.loadStart(false);

        PluginCommand command = this.getCommand("clv");
        if (command != null) {
            command.setExecutor(new CLVCommand(this));
            command.setTabCompleter(new CLVTabComplete(this));
        }

        reloadPlugin();
        core.loadFinish();
    }

    public void reloadPlugin() {
        shutdownRuntime();

        (listeners = new Listeners(this)).register();
        cache = new Cache(this);

        long start = System.currentTimeMillis();
        BaseSystem<?> system = !cache.config().useBigDecimalSystem() ?
                new DoubleSystem(this) :
                new BigDecimalSystem(this);

        logger("&dChecking level system type...");
        levelSystem = system;

        logger(
                "&7Loaded &e" + system.getClass().getSimpleName() +
                        "&7 in &a" +
                        (System.currentTimeMillis() - start) +
                        "ms&7.", ""
        );

        UserManagerImpl<?> manager = new UserManagerImpl<>(this, system);
        manager.checkMigration();

        database = (userManager = manager).getDatabase();
        logger("");

        manager.loadOfflinePlayers();
        userManager.loadOnlinePlayers();

        cache.loadSecondaryFiles();

        cache.earnExp().register();
        cache.antiAbuse().register();

        (hookManager = new HookManager(this)).register();
        userManager.startAutoSave();

        levelSystem.getLeaderboard().update();
    }

    private void shutdownRuntime() {
        if (userManager != null) {
            userManager.cancelAutoSave();

            if (userManager instanceof UserManagerImpl<?>)
                ((UserManagerImpl<?>) userManager).saveOnlinePlayersSync(true);
            else userManager.saveOnlinePlayers(true);
        }

        if (cache != null) {
            cache.antiAbuse().unregister();
            cache.earnExp().unregister();
        }

        if (hookManager != null) hookManager.unregister();

        if (database != null) {
            database.disconnect();
            database = null;
        }

        if (listeners != null) {
            listeners.unregister();
            listeners = null;
        }

        hookManager = null;
        userManager = null;
        levelSystem = null;
        cache = null;
    }

    @Override
    public void onDisable() {
        shutdownRuntime();
    }

    public String getAuthors() {
        return this.getDescription().getAuthors().toString().replaceAll("[\\[\\]]", "");
    }

    public double serverVersion() {
        return ServerInfoUtils.SERVER_VERSION;
    }

    public TakionLib library() {
        return core.getLibrary();
    }

    public MessageSender createSender(Player player) {
        return library().getLoadedSender().setTargets(player).setParser(player);
    }

    public void logger(String... message) {
        library().getLogger().log(message);
    }

    public boolean isEnabled(String plugin) {
        return Bukkit.getPluginManager().getPlugin(plugin) != null;
    }
}
