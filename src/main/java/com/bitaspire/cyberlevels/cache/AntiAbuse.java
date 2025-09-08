package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.level.ExpSource;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.croabeast.file.Configurable;
import me.croabeast.file.ConfigurableFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class AntiAbuse {

    private final CyberLevels main;

    @Accessors(fluent = true)
    private boolean onlyNaturalBlocks = false, includeNaturalCrops = false;

    private boolean silkTouchEnabled = false;
    private final Map<String, Module> modules = new HashMap<>();

    AntiAbuse(CyberLevels main) {
        this.main = main;

        try {
            long start = System.currentTimeMillis();
            main.logger("&dLoading anti-abuse...");

            ConfigurableFile file = new CLVFile(main, "anti-abuse");

            silkTouchEnabled = file.get("anti-abuse.general.silk-touch-reward", false);
            onlyNaturalBlocks = file.get("anti-abuse.general.only-natural-blocks", false);
            includeNaturalCrops = file.get("anti-abuse.general.include-natural-crops", false);

            for (String key : file.getKeys("anti-abuse")) {
                if (key.equals("general")) continue;

                ConfigurationSection section = file.getSection("anti-abuse." + key);
                if (section != null) modules.put(key, new Module(section));
            }

            main.logger("&7Loaded &e" + (modules.size()) + "&7 anti-abuse settings in &a" + (System.currentTimeMillis() - start) + "ms&7.", "");
        }
        catch (IOException ignored) {}
    }

    @NotNull
    public Map<String, com.bitaspire.cyberlevels.level.AntiAbuse> getAntiAbuses() {
        return new HashMap<>(modules);
    }

    public void register() {
        for (Module module : modules.values()) {
            module.getTimer().start();
        }
    }

    public void unregister() {
        for (Module module : modules.values()) {
            module.cancelTimer();
            module.resetCooldowns();
            module.resetLimiters();
        }
    }

    @Getter
    class Module implements com.bitaspire.cyberlevels.level.AntiAbuse {

        private final List<String> expEvents = new ArrayList<>();

        private final boolean cooldownEnabled;
        private final int cooldownTime;

        private final boolean limiterEnabled;
        private final long limiterAmount;

        private final boolean worldsEnabled, worldsWhitelist;
        private Timer timer = null;

        private final Map<Player, Long> cooldowns = new HashMap<>(), limiters = new HashMap<>();
        private final List<String> worldsList = new ArrayList<>();

        Module(ConfigurationSection section) {
            expEvents.addAll(Configurable.toStringList(section, "exp-events"));

            cooldownEnabled = section.getBoolean("cooldown.enabled", false);
            cooldownTime = section.getInt("cooldown.time", 5);

            limiterEnabled = section.getBoolean("limiter.enabled", false);
            limiterAmount = section.getLong("limiter.amount", 250);

            String timer = section.getString("limiter.timer", "2022-01-15 00:00 6h");
            try {
                this.timer = new Timer(main, this, timer);
            } catch (Exception ignored) {}

            worldsEnabled = section.getBoolean("worlds.enabled", false);
            worldsWhitelist = section.getBoolean("worlds.whitelist", true);
            worldsList.addAll(Configurable.toStringList(section, "worlds.list"));
        }

        @Override
        public int getCooldownLeft(Player player) {
            Long last = cooldowns.get(player);
            if (last == null) return 0;

            long elapsed = System.currentTimeMillis() - last;
            return Math.toIntExact(elapsed >= cooldownTime ? 0 : elapsed);
        }

        @Override
        public void resetCooldowns() {
            cooldowns.clear();
        }

        @Override
        public void resetCooldown(Player player) {
            cooldowns.remove(player);
        }

        @Override
        public int getLimiter(Player player) {
            return Math.toIntExact(limiters.getOrDefault(player, limiterAmount));
        }

        @Override
        public void resetLimiters() {
            limiters.clear();
        }

        @Override
        public void resetLimiter(Player player) {
            limiters.remove(player);
        }

        @Override
        public void cancelTimer() {
            if (timer == null) return;

            timer.purge();
            timer = null;
        }

        boolean isWorldLimited(Player player, String event) {
            return expEvents.contains(event) &&
                    worldsEnabled &&
                    worldsWhitelist != worldsList.contains(player.getWorld().getName());
        }

        boolean isCoolingDown(Player player, String event) {
            if (!expEvents.contains(event) || !cooldownEnabled)
                return false;

            long now = System.currentTimeMillis();
            long last = cooldowns.getOrDefault(player, 0L);

            if (now - last >= cooldownTime) {
                cooldowns.put(player, now);
                return false;
            }

            return true;
        }

        boolean isLimited(Player player, String event) {
            if (!expEvents.contains(event) || !limiterEnabled) return false;

            long remaining = limiters.getOrDefault(player, limiterAmount);
            if (remaining <= 0) return true;

            limiters.put(player, remaining - 1);
            return false;
        }

        @Override
        public boolean isLimited(Player player, ExpSource source) {
            final String name = source.getCategory();
            return isWorldLimited(player, name) || isCoolingDown(player, name) || isLimited(player, name);
        }
    }
}
