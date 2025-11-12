package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.cache.Cache;
import com.bitaspire.cyberlevels.command.CLVCommand;
import com.bitaspire.cyberlevels.command.CLVTabComplete;
import com.bitaspire.cyberlevels.hook.HookManager;
import com.bitaspire.cyberlevels.level.LevelSystem;
import com.bitaspire.cyberlevels.listener.Listeners;
import com.bitaspire.cyberlevels.user.Database;
import com.bitaspire.cyberlevels.user.UserManager;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.croabeast.beanslib.utility.LibUtils;
import me.croabeast.scheduler.GlobalScheduler;
import net.zerotoil.dev.cybercore.CoreSettings;
import net.zerotoil.dev.cybercore.CyberCore;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
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
        if (!CyberCore.restrictVersions(8, 22, "CLV", getDescription().getVersion()))
            return;

        instance = this;
        scheduler = GlobalScheduler.getScheduler(this);
        core = new CyberCore(this);

        CoreSettings settings = core.coreSettings();
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
            command.setTabCompleter(new CLVTabComplete());
        }

        reloadPlugin();
        core.loadFinish();
    }

    public void reloadPlugin() {
        if (cache != null) {
            cache.antiAbuse().unregister();
            cache.earnExp().unregister();
        }

        if (hookManager != null) hookManager.unregister();

        (listeners = new Listeners(this)).register();
        cache = new Cache(this);

        long start = System.currentTimeMillis();
        BaseSystem<?> system = !cache.config().useBigDecimalSystem() ?
                new DoubleLevelSystem(this) :
                new BigDecimalLevelSystem(this);

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

        manager.loadOfflinePlayers();
        userManager.loadOnlinePlayers();

        cache.loadSecondaryFiles();

        cache.earnExp().register();
        cache.antiAbuse().register();

        (hookManager = new HookManager(this)).register();
        userManager.startAutoSave();

        levelSystem.getLeaderboard().update();
    }

    @Override
    public void onDisable() {
        cache.antiAbuse().unregister();
        cache.earnExp().unregister();

        hookManager.unregister();

        userManager.saveOnlinePlayers(true);
        userManager.cancelAutoSave();

        if (database != null) database.disconnect();

        listeners.unregister();
    }

    public String getAuthors() {
        return this.getDescription().getAuthors().toString().replace("[", "").replace("]", "");
    }

    public double serverVersion() {
        return LibUtils.getMainVersion();
    }

    public void logger(String... message) {
        core.logger(message);
    }

    public boolean isEnabled(String plugin) {
        return Bukkit.getPluginManager().getPlugin(plugin) != null;
    }
}
