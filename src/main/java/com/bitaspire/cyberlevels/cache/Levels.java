package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed view of the level progression configuration.
 *
 * <p>This cache stores the starting point of the progression system, the configured maximum level,
 * the default experience formula, and any per-level formula overrides defined in
 * {@code levels.yml}. The values are later consumed by the active {@code LevelSystem}
 * implementation when calculating required experience.
 */
@Getter
public class Levels {

    private long startLevel = 1;
    private int startExp = 0;

    private long maxLevel = 25;

    private String formula = "25 * {level}";
    private final Map<Long, String> customFormulas = new HashMap<>();

    Levels(CyberLevels main) {
        try {
            CLVFile file = new CLVFile(main, "levels");

            startLevel = Long.parseLong(file.get("levels.starting.level", 1) + "");
            startExp = file.get("levels.starting.experience", 0);

            maxLevel = Long.parseLong(file.get("levels.maximum.level", 25) + "");

            formula = file.get("levels.experience.general-formula", formula);
            for (String key : file.getKeys("levels.experience.level", false))
                try {
                    Long parsed = Long.parseLong(key);

                    String value = file.get("levels.experience.level." + key, new Object()).toString();
                    if (StringUtils.isBlank(value)) continue;

                    customFormulas.put(parsed, value);
                }
                catch (Exception ignored) {}
        }
        catch (IOException ignored) {}
    }
}
