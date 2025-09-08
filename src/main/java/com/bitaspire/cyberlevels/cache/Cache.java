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

    private AntiAbuse antiAbuse;
    private EarnExp earnExp;

    public Cache(CyberLevels main) {
        this.main = main;

        long start = System.currentTimeMillis();
        main.logger("&dLoading main files...");

        config = new Config(main);
        lang = new Lang(main);
        levels = new Levels(main);

        main.logger("&7Loaded &e3 &7main files in &a" + (System.currentTimeMillis() - start) + "ms&7.", "");
    }

    public void loadSecondaryFiles() {
        new Rewards(main);
        antiAbuse = new AntiAbuse(main);
        earnExp = new EarnExp(main);
    }
}
