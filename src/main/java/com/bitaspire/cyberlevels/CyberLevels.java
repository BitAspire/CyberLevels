package com.bitaspire.cyberlevels;

import com.bitaspire.libs.common.CollectionBuilder;
import com.bitaspire.libs.common.util.ServerInfoUtils;
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
import com.bitaspire.cyberlevels.utility.SpigotUpdateChecker;
import com.bitaspire.libs.scheduler.GlobalScheduler;
import com.bitaspire.libs.takion.TakionLib;
import com.bitaspire.libs.takion.message.MessageSender;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.zerotoil.dev.cyberlevels.api.events.XPChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Main plugin entry point for CyberLevels.
 *
 * <p>This class owns the full runtime lifecycle of the plugin: dependency bootstrap, command
 * registration, cache loading, user/database startup, hook registration, leaderboard refreshes,
 * and final shutdown. External plugins commonly reach CyberLevels services through the generated
 * getters exposed here, such as {@link #cache()}, {@link #levelSystem()}, {@link #userManager()},
 * and {@link #library()}.
 *
 * <p>The runtime can also be rebuilt in-place through {@link #reloadPlugin()}, which safely tears
 * down active listeners, hooks, scheduled tasks, and persistence components before creating fresh
 * instances from the current configuration files.
 */
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

    /**
     * Cached Spigot update notice that can be delivered to operators after the asynchronous
     * version check completes.
     *
     * <p>The notice only stores normalized state and version strings. The final chat lines are
     * still resolved from {@code lang.yml} at send time so administrators can localize or restyle
     * the message without changing code.
     */
    @Getter(AccessLevel.NONE)
    private volatile SpigotOpUpdateNotice spigotOpUpdateNotice = SpigotOpUpdateNotice.none();

    /**
     * Boots the plugin and creates the first live runtime.
     *
     * <p>This method loads legacy dependencies when needed, initializes CyberCore and Takion,
     * registers the base command executor/tab completer, and then delegates the actual runtime
     * construction to {@link #reloadPlugin()}. Calling the shared reload path keeps startup and
     * manual reload behaviour aligned.
     */
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

    /**
     * Rebuilds the active plugin runtime from disk and from the current server state.
     *
     * <p>The previous runtime is stopped first through an internal shutdown routine so the reload
     * can happen without duplicating listeners, hooks, database connections, or auto-save tasks.
     * After that, this method recreates caches, picks the numeric engine, reloads users, registers
     * event sources and anti-abuse modules, refreshes the leaderboard, and optionally schedules the
     * Spigot update check.
     */
    @SuppressWarnings("deprecation")
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

        if (cache.config().isSpigotUpdateCheckEnabled())
            SpigotUpdateChecker.checkAsync(this);

        scheduler.runTaskLater(() -> {
            List<String> list = CollectionBuilder
                    .of(XPChangeEvent.getHandlerList().getRegisteredListeners())
                    .map(l -> l.getPlugin().getName()).toList();
            if (!list.isEmpty())
                library().getLogger().log(
                        "Detected plugins still listening to deprecated XPChangeEvent: " + String.join(", ", list),
                        "Ask those plugins to migrate to com.bitaspire.cyberlevels.event.ExpChangeEvent."
                );
        }, 1L);
    }

    private void shutdownRuntime() {
        if (userManager != null) {
            userManager.cancelAutoSave();

            if (userManager instanceof UserManagerImpl<?>) {
                ((UserManagerImpl<?>) userManager).saveOnlinePlayersSync(true);
            } else {
                userManager.saveOnlinePlayers(true);
            }
        }

        if (cache != null) {
            cache.antiAbuse().unregister();
            cache.earnExp().unregister();
        }

        if (hookManager != null) hookManager.unregister();

        if (database != null) {
            if (database instanceof DatabaseFactory.DatabaseImpl<?>) {
                ((DatabaseFactory.DatabaseImpl<?>) database).disconnectSync();
            } else {
                database.disconnect();
            }
            database = null;
            if (isEnabled()) logger("");
        }

        spigotOpUpdateNotice = SpigotOpUpdateNotice.none();

        if (listeners != null) {
            listeners.unregister();
            listeners = null;
        }

        hookManager = null;
        userManager = null;
        levelSystem = null;
        cache = null;
    }

    /**
     * Shuts down the active runtime when Bukkit disables the plugin.
     *
     * <p>This flushes user data, stops auto-save and hooks, unregisters listeners, disconnects the
     * database, and clears transient references so the JVM can release the old runtime cleanly.
     */
    @Override
    public void onDisable() {
        shutdownRuntime();
    }

    /**
     * Returns the plugin authors exactly as declared in the plugin description.
     *
     * <p>The backing Bukkit descriptor stores authors as a list. This helper flattens that list to
     * the human-readable comma-separated format used in startup logs and the {@code /clv about}
     * output.
     *
     * @return author list formatted for display
     */
    public String getAuthors() {
        return this.getDescription().getAuthors().toString().replaceAll("[\\[\\]]", "");
    }

    /**
     * Returns the detected server version as a numeric major/minor value.
     *
     * <p>This is primarily used by internal compatibility branches, for example when deciding which
     * Bukkit data APIs are available for crops, block states, or dependency bootstrapping.
     *
     * @return parsed server version such as {@code 20.4}
     */
    public double serverVersion() {
        return ServerInfoUtils.SERVER_VERSION;
    }

    /**
     * Exposes the shared Takion library instance initialized through CyberCore.
     *
     * <p>Consumers can use this to access the loaded message sender, logger, and other library
     * services that CyberLevels already wires and configures during startup.
     *
     * @return active Takion library facade
     */
    public TakionLib library() {
        return core.getLibrary();
    }

    /**
     * Creates a preconfigured message sender targeting a specific player.
     *
     * <p>The returned sender already has the player bound both as the output target and as the
     * placeholder parser context, which makes it suitable for one-off plugin messages without
     * having to repeat the same setup each time.
     *
     * @param player player that should receive and parse the message
     * @return configured sender instance
     */
    public MessageSender createSender(Player player) {
        return library().getLoadedSender().setTargets(player).setParser(player);
    }

    /**
     * Writes one or more lines to the plugin logger through Takion's formatting pipeline.
     *
     * <p>This helper is used throughout the plugin to keep console formatting consistent with the
     * rest of the CyberCore ecosystem.
     *
     * @param message console lines to print in order
     */
    public void logger(String... message) {
        library().getLogger().log(message);
    }

    /**
     * Checks whether Bukkit currently exposes a plugin instance under the given name.
     *
     * <p>This is used as a lightweight presence check before optional integrations are created. The
     * method only verifies that the plugin can be resolved from the plugin manager; it does not
     * perform any extra capability probing beyond that lookup.
     *
     * @param plugin plugin name as registered with Bukkit
     * @return {@code true} when a plugin with that name is present, otherwise {@code false}
     */
    public boolean isEnabled(String plugin) {
        return Bukkit.getPluginManager().getPlugin(plugin) != null;
    }

    /**
     * Replaces the cached operator-facing Spigot update notice.
     *
     * <p>Passing {@code null} clears the notice and falls back to {@link SpigotOpUpdateNotice#none()}
     * so callers do not need to perform null checks before updating the cache.
     *
     * @param notice pending notice from the Spigot version check, or {@code null} to clear it
     */
    public void setSpigotOpUpdateNotice(SpigotOpUpdateNotice notice) {
        spigotOpUpdateNotice = notice != null ? notice : SpigotOpUpdateNotice.none();
    }

    /**
     * Returns the cached Spigot update notice prepared for operators.
     *
     * <p>The returned object is immutable and can safely be shared between the asynchronous update
     * checker and the synchronous chat delivery path.
     *
     * @return current cached notice, never {@code null}
     */
    public SpigotOpUpdateNotice getSpigotOpUpdateNotice() {
        return spigotOpUpdateNotice;
    }

    /**
     * Immutable snapshot describing the relationship between the local JAR version and the version
     * currently listed on Spigot.
     *
     * <p>This type intentionally carries only normalized metadata. User-facing formatting remains in
     * {@code lang.yml}, which keeps localization and styling outside of the code path.
     */
    public static final class SpigotOpUpdateNotice {

        /**
         * Notice kind used when no operator message should be shown.
         */
        public static final byte KIND_NONE = 0;
        /**
         * Notice kind used when Spigot lists a newer public version than the local JAR.
         */
        public static final byte KIND_NEWER = 1;
        /**
         * Notice kind used when the local JAR is newer than the currently listed Spigot version.
         */
        public static final byte KIND_EARLY = 2;

        private final byte kind;
        private final @Nullable String remoteVersion;
        private final @Nullable String localVersion;

        private SpigotOpUpdateNotice(byte kind, @Nullable String remoteVersion, @Nullable String localVersion) {
            this.kind = kind;
            this.remoteVersion = remoteVersion;
            this.localVersion = localVersion;
        }

        /**
         * Creates an empty notice representing the absence of update information.
         *
         * @return immutable notice with {@link #KIND_NONE}
         */
        public static SpigotOpUpdateNotice none() {
            return new SpigotOpUpdateNotice(KIND_NONE, null, null);
        }

        /**
         * Creates a notice indicating that Spigot exposes a newer version than the one running on
         * the server.
         *
         * @param remoteVersion version reported by Spigot
         * @param localVersion version currently running on the server
         * @return immutable notice with {@link #KIND_NEWER}
         */
        public static SpigotOpUpdateNotice newer(String remoteVersion, String localVersion) {
            return new SpigotOpUpdateNotice(KIND_NEWER, remoteVersion, localVersion);
        }

        /**
         * Creates a notice indicating that the server is running an early-access build newer than
         * the Spigot listing.
         *
         * @param localVersion version currently running on the server
         * @return immutable notice with {@link #KIND_EARLY}
         */
        public static SpigotOpUpdateNotice earlyAccess(String localVersion) {
            return new SpigotOpUpdateNotice(KIND_EARLY, null, localVersion);
        }

        /**
         * Returns the notice kind constant.
         *
         * @return one of {@link #KIND_NONE}, {@link #KIND_NEWER}, or {@link #KIND_EARLY}
         */
        public byte getKind() {
            return kind;
        }

        /**
         * Returns the remote version reported by Spigot when applicable.
         *
         * @return remote version for {@link #KIND_NEWER}, otherwise {@code null}
         */
        public @Nullable String getRemoteVersion() {
            return remoteVersion;
        }

        /**
         * Returns the local plugin version captured when the notice was created.
         *
         * @return local plugin version for update-related notices, otherwise {@code null}
         */
        public @Nullable String getLocalVersion() {
            return localVersion;
        }
    }
}
