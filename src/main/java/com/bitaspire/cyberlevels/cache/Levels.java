package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import lombok.AccessLevel;
import lombok.Getter;
import me.croabeast.file.ConfigurableFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Getter
public class Levels {

    private long startLevel = 1;
    private int startExp = 0;

    private long maxLevel = 25;

    private String formula = "25 * {level}";
    @Getter(AccessLevel.NONE)
    private final Map<Long, String> customFormulas = new HashMap<>();

    Levels(CyberLevels main) {
        try {
            ConfigurableFile file = new CLVFile(main, "levels");

            startLevel = Long.parseLong(file.get("levels.starting.level", 1) + "");
            startExp = file.get("levels.starting.experience", 0);

            maxLevel = Long.parseLong(file.get("levels.maximum.level", 25) + "");

            formula = file.get("levels.experience.general-formula", formula);
            for (String key : file.getKeys("levels.experience.level"))
                try {
                    Long parsed = Long.parseLong(key);

                    String value = file.get("levels.experience.level." + key, "");
                    if (StringUtils.isBlank(value)) continue;

                    customFormulas.put(parsed, value);
                }
                catch (Exception ignored) {}
        }
        catch (IOException ignored) {}
    }

    @Nullable
    public String getCustomFormula(@Nullable Long level) {
        return level != null ? customFormulas.get(level) : formula;
    }
}
