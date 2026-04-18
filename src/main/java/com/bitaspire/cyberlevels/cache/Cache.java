package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Aggregates the plugin configuration caches loaded from disk.
 *
 * <p>The cache is split into two stages. Core files such as {@code config.yml}, {@code lang.yml},
 * {@code levels.yml}, and {@code rewards.yml} are loaded in the constructor because other startup
 * systems depend on them immediately. Heavier secondary files such as anti-abuse and earn-exp are
 * loaded later through {@link #loadSecondaryFiles()} once the numeric engine and user manager have
 * already been prepared.
 */
@Accessors(fluent = true)
@Getter
public class Cache {

    @Getter(AccessLevel.NONE)
    private final CyberLevels main;

    private final Config config;
    private final Lang lang;
    private final Levels levels;
    private final Rewards rewards;

    private AntiAbuse antiAbuse;
    private EarnExp earnExp;

    /**
     * Loads the primary configuration caches required for the initial plugin bootstrap.
     *
     * @param main owning plugin instance
     */
    public Cache(CyberLevels main) {
        this.main = main;

        long start = System.currentTimeMillis();
        main.logger("&dLoading main files...");

        config = new Config(main);
        lang = new Lang(main);
        levels = new Levels(main);
        rewards = new Rewards(main);

        if (config.autoUpdateConfig())
            config.update();

        if (config.autoUpdateLang())
            lang.update();

        main.logger("&7Loaded &e4 &7main files in &a" + (System.currentTimeMillis() - start) + "ms&7.", "");
    }

    /**
     * Loads the secondary caches that depend on the already-initialized runtime.
     *
     * <p>This stage creates anti-abuse and earn-exp data after the main cache, level system, and
     * user services are available, which avoids partially initialized state during startup or
     * reloads.
     */
    public void loadSecondaryFiles() {
        antiAbuse = new AntiAbuse(main);

        earnExp = new EarnExp(main);
        if (config.autoUpdateEarnExp())
            earnExp.update();
    }
}
