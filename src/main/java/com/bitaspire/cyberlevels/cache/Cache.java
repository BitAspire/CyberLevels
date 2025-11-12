package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

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

    public void loadSecondaryFiles() {
        antiAbuse = new AntiAbuse(main);

        earnExp = new EarnExp(main);
        if (config.autoUpdateEarnExp())
            earnExp.update();
    }
}
