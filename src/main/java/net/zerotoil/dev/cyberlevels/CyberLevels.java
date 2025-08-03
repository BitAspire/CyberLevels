package net.zerotoil.dev.cyberlevels;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.zerotoil.dev.cybercore.CoreSettings;
import net.zerotoil.dev.cybercore.CyberCore;
import net.zerotoil.dev.cybercore.files.FileManager;
import net.zerotoil.dev.cyberlevels.commands.CLVCommand;
import net.zerotoil.dev.cyberlevels.commands.CLVTabComplete;
import net.zerotoil.dev.cyberlevels.listeners.AntiAbuseListeners;
import net.zerotoil.dev.cyberlevels.listeners.EXPListeners;
import net.zerotoil.dev.cyberlevels.listeners.JoinListener;
import net.zerotoil.dev.cyberlevels.objects.exp.EXPCache;
import net.zerotoil.dev.cyberlevels.objects.levels.LevelCache;
import net.zerotoil.dev.cyberlevels.objects.files.Files;
import net.zerotoil.dev.cyberlevels.utilities.LangUtils;
import net.zerotoil.dev.cyberlevels.utilities.LevelUtils;
import net.zerotoil.dev.cyberlevels.utilities.PlayerUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;
import me.croabeast.beanslib.Beans;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class CyberLevels extends JavaPlugin {

    private CyberCore core;

    LangUtils langUtils;
    LevelUtils levelUtils;
    PlayerUtils playerUtils;

    LevelCache levelCache;
    EXPCache expCache;

    EXPListeners expListeners;

    @Override
    public void onEnable() {

        if (!CyberCore.restrictVersions(8, 22, "CLV", getDescription().getVersion())) return;

        reloadCore();
        loadPlugin();
        core.loadFinish();
    }

    private void loadPlugin() {
        playerUtils = new PlayerUtils(this);
        expListeners = new EXPListeners(this);
        new AntiAbuseListeners(this);

        new CLVCommand(this);
        new CLVTabComplete(this);
        new JoinListener(this);
        reloadPlugin();
    }

    public void reloadPlugin() {
        if (expCache != null) {
            expCache.cancelTimedEXP();
            expCache.cancelAntiAbuseTimers();
        }
        langUtils = new LangUtils(this);
        levelUtils = new LevelUtils(this);
        levelCache = new LevelCache(this);
        expCache = new EXPCache(this);

        levelCache.loadLevelData();
        levelCache.loadOnlinePlayers();
        levelCache.loadRewards();
        levelCache.loadLeaderboard();
    }

    /**
     * Reload the core of the plugin (CyberCore).
     */
    public void reloadCore() {
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
                "&7╱╱╱╱&d╭━╯┃  &7Author: &f Kihsomray",
                "&7╱╱╱╱&d╰━━╯  &7Version: &f" + this.getDescription().getVersion()
        );

        core.loadStart("plugin-data", "anti-abuse", "levels", "earn-exp", "rewards");
    }

    @Override
    public void onDisable() {
        levelCache.saveOnlinePlayers(true);
        levelCache.clearLevelData();
        levelCache.cancelAutoSave();
        // stuff

        if (levelCache.getMySQL() != null) levelCache.getMySQL().disconnect();
    }

    /**
     * Gets the author of the plugin.
     *
     * @return Author of XenoLevels.
     */
    public String getAuthors() {
        return this.getDescription().getAuthors().toString().replace("[", "").replace("]", "");
    }

    public int serverVersion() {
        return Integer.parseInt(Bukkit.getBukkitVersion().split("-")[0].split("\\.")[1]);
    }

    public void logger(String... message) {
        Beans.doLog(message);
    }

    /**
     * Gets the instance of the plugin.
     *
     * @return XenoLevels instance
     */
    public static CyberLevels instance() {
        return JavaPlugin.getPlugin(CyberLevels.class);
    }

    /**
     * Checks if a certain plugin is enabled within
     * the Bukkit system.
     *
     * @param plugin Plugin to check
     * @return True if enabled
     */
    public boolean isEnabled(String plugin) {
        return Bukkit.getPluginManager().getPlugin(plugin) != null;
    }

    /**
     * Gets the core (CyberCore) of the plugin.
     *
     * @return CyberCore instance
     */
    public CyberCore core() {
        return core;
    }

    /**
     * Gets the file manager of the plugin.
     *
     * @return FileManager instance
     */
    public FileManager files() {
        return core.files();
    }

    /**
     * Gets a specific config from files
     *
     * @param config Name of config (without extension)
     * @return Configuration of the file
     */
    public Configuration getConfig(String config) {
        return core.files().getConfig(config);
    }

}
