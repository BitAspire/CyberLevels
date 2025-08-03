package com.bitaspire.xenolevels.hook;

import lombok.Getter;
import net.zerotoil.dev.cybercore.addons.Metrics;
import net.zerotoil.dev.cyberlevels.CyberLevels;
import net.zerotoil.dev.cyberlevels.addons.PlaceholderAPI;
import net.zerotoil.dev.cyberlevels.listeners.hooks.RivalHarvesterHoesHook;
import org.bukkit.Bukkit;

/**
 * This class manages plugin hooks.
 *
 * @author Kihsomray
 */
public class HookManager {

    @Getter private final Metrics metrics;
    /**
     * Constructor for hook manager.
     */

    public HookManager() {
        metrics = new Metrics(CyberLevels.instance(), 13782);
        reload();
    }

    /**
     * Reloads all hook settings.
     */
    public void reload() {

        final CyberLevels main = CyberLevels.instance();
        main.logger("&bLoading plugin hooks...");
        long startTime = System.currentTimeMillis();
        int counter = 0;

        if (main.isEnabled("PlaceholderAPI")) {
            final long startTimePAPI = System.currentTimeMillis();
            new PlaceholderAPI(main).register();
            main.logger("&7Loaded &ePlaceholderAPI&7 plugin hook in &a" + (System.currentTimeMillis() - startTimePAPI) + "ms&7.");
            counter++;
        }

        if (main.isEnabled("RivalHarvesterHoes")) {
            final long startTimeRivalHoes = System.currentTimeMillis();
            new RivalHarvesterHoesHook();
            main.logger("&7Loaded &eRivalHarvesterHoes&7 plugin hook in &a" + (System.currentTimeMillis() - startTimeRivalHoes) + "ms&7.");
            counter++;
        }

        // Rival Pickaxes
        if (main.isEnabled("RivalPickaxes")) {
            // TODO implement RivalPickaxesHook
            //new RivalPickaxesHook(this);
            // counter++;
        }

        main.logger("&7Loaded &e" + counter + "&7 plugin hook" + (counter == 1 ? "" : "s") + " in &a" + (System.currentTimeMillis() - startTime) + "ms&7.", "");

    }

}
