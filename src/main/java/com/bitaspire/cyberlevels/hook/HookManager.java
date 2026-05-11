package com.bitaspire.cyberlevels.hook;

import com.bitaspire.libs.common.MetricsLoader;
import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.level.ExpSource;
import com.bitaspire.cyberlevels.user.LevelUser;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects, initializes, and manages optional third-party integrations.
 *
 * <p>The hook manager is responsible for loading supported integrations only when their
 * dependencies are present on the server. It also initializes plugin metrics and offers a shared
 * helper path for integrations that need to forward EXP gains back into the active level system.
 */
public class HookManager {

    private final Set<Hook> hooks = new HashSet<>();
    private final CyberLevels main;

    /**
     * Creates and eagerly loads every supported integration that is available on the server.
     *
     * @param main owning plugin instance
     */
    public HookManager(CyberLevels main) {
        (this.main = main).logger("&dLoading plugin hooks...");

        long startTime = System.currentTimeMillis();

        System.setProperty("bstats.relocatecheck", "false");
        MetricsLoader.initialize(main, 13782);

        if (main.isEnabled("PlaceholderAPI")) {
            final long l = System.currentTimeMillis();
            hooks.add(new PlaceholderAPI(main));
            main.logger("&7Loaded &ePlaceholderAPI&7 plugin hook in &a" + (System.currentTimeMillis() - l) + "ms&7.");
        }

        if (main.isEnabled("RivalHarvesterHoes")) {
            final long l = System.currentTimeMillis();
            hooks.add(new RivalHoesHook(main, this));
            main.logger("&7Loaded &eRivalHarvesterHoes&7 plugin hook in &a" + (System.currentTimeMillis() - l) + "ms&7.");
        }

        if (main.isEnabled("RivalPickaxes")) {
            final long l = System.currentTimeMillis();
            hooks.add(new RivalPickHook(main, this));
            main.logger("&7Loaded &eRivalPickaxes&7 plugin hook in &a" + (System.currentTimeMillis() - l) + "ms&7.");
        }

        int c = hooks.size();
        main.logger("&7Loaded &e" + c + "&7 plugin hook" +
                (c == 1 ? "" : "s") +
                " in &a" + (System.currentTimeMillis() - startTime) +
                "ms&7.", "");
    }

    void sendExp(Player player, ExpSource source, String item) {
        if (main.levelSystem().checkAntiAbuse(player, source)) return;

        double counter = 0;
        String matched = source.useSpecifics() ? source.matchSpecificKey(item) : null;

        if (source.isEnabled() &&
                source.isInList(item) &&
                (matched == null || source.stackSpecificsWithGeneral()))
            counter += source.getRange().getRandom();

        if (matched != null)
            counter += source.getSpecificRange(matched).getRandom();

        if (counter == 0) return;

        LevelUser<?> user = main.userManager().getUser(player);
        if (counter > 0) {
            user.addExp(counter, main.cache().config().isMultiplierEvents());
            return;
        }

        user.removeExp(Math.abs(counter));
    }

    /**
     * Registers all loaded hooks with their respective target plugins or services.
     */
    public void register() {
        hooks.forEach(Hook::register);
    }

    /**
     * Unregisters all loaded hooks and clears the hook registry.
     *
     * <p>This is called during runtime shutdown so no old integration state remains attached after a
     * reload.
     */
    public void unregister() {
        hooks.forEach(Hook::unregister);
        hooks.clear();
    }
}
