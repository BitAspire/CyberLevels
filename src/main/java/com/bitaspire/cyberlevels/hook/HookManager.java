package com.bitaspire.cyberlevels.hook;

import com.bitaspire.libs.common.MetricsLoader;
import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.level.ExpSource;
import com.bitaspire.cyberlevels.user.LevelUser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

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
    private AxBoostersHook axBoostersHook;
    private boolean axHoesLoaded;
    private boolean axPickLoaded;
    private boolean globalRegistered;
    private final LateHookListener lateListener = new LateHookListener();

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

        if (main.isEnabled("AxBoosters")) {
            final long l = System.currentTimeMillis();
            axBoostersHook = new AxBoostersHook(main);
            hooks.add(axBoostersHook);
            main.logger("&7Loaded &eAxBoosters&7 plugin hook in &a" + (System.currentTimeMillis() - l) + "ms&7.");
        }

        // AxHoes and AxPickaxes both list CyberLevels in their own softdepend, which makes Bukkit
        // commonly enable them AFTER us — so a plain isEnabled() check at construction misses them.
        // We try to load eagerly here when possible, then attach a PluginEnableEvent listener as a
        // fallback that activates the hook the moment those plugins finish enabling.
        loadAxHoesIfReady();
        loadAxPickIfReady();

        int c = hooks.size();
        main.logger("&7Loaded &e" + c + "&7 plugin hook" +
                (c == 1 ? "" : "s") +
                " in &a" + (System.currentTimeMillis() - startTime) +
                "ms&7.", "");
    }

    private synchronized boolean loadAxHoesIfReady() {
        if (axHoesLoaded) return false;
        if (!main.isEnabled("AxHoes")) return false;

        final long l = System.currentTimeMillis();
        AxHoesHook hook = new AxHoesHook(main, this);
        hooks.add(hook);
        // Only register here if the global register() pass has already run; otherwise it'll be
        // picked up by hooks.forEach(Hook::register) later. Registering twice would attach the
        // listener twice and double-count every PlayerXPGainEvent.
        if (globalRegistered) hook.register();
        axHoesLoaded = true;
        main.logger("&7Loaded &eAxHoes&7 plugin hook in &a" + (System.currentTimeMillis() - l) + "ms&7.");
        return true;
    }

    private synchronized boolean loadAxPickIfReady() {
        if (axPickLoaded) return false;
        if (!main.isEnabled("AxPickaxes")) return false;

        final long l = System.currentTimeMillis();
        AxPickHook hook = new AxPickHook(main, this);
        hooks.add(hook);
        if (globalRegistered) hook.register();
        axPickLoaded = true;
        main.logger("&7Loaded &eAxPickaxes&7 plugin hook in &a" + (System.currentTimeMillis() - l) + "ms&7.");
        return true;
    }

    void sendExp(Player player, ExpSource source, String item) {
        if (source == null) return;
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
     * Returns the multiplier currently applied to {@code player} by external boost integrations.
     *
     * <p>This combines every loaded multiplier-providing hook (currently AxBoosters) into a single
     * scalar that callers can apply on top of permission-based multipliers.
     *
     * @param player target player; {@code null} returns 1.0
     * @return non-negative multiplier; 1.0 when no boost is active
     */
    public double externalMultiplier(Player player) {
        if (player == null) return 1D;

        double multiplier = 1D;
        if (axBoostersHook != null) multiplier *= axBoostersHook.getMultiplier(player);
        return multiplier;
    }

    /**
     * Registers all loaded hooks with their respective target plugins or services.
     */
    public void register() {
        hooks.forEach(Hook::register);
        // Catch plugins that enable AFTER us — primarily AxHoes and AxPickaxes, which softdepend
        // on CyberLevels and therefore typically load later in the plugin enable order.
        main.getServer().getPluginManager().registerEvents(lateListener, main);
        globalRegistered = true;
    }

    /**
     * Unregisters all loaded hooks and clears the hook registry.
     *
     * <p>This is called during runtime shutdown so no old integration state remains attached after a
     * reload.
     */
    public void unregister() {
        HandlerList.unregisterAll(lateListener);
        hooks.forEach(Hook::unregister);
        hooks.clear();
        axHoesLoaded = false;
        axPickLoaded = false;
        globalRegistered = false;
    }

    private final class LateHookListener implements Listener {

        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            String name = event.getPlugin().getName();
            if ("AxHoes".equals(name)) loadAxHoesIfReady();
            else if ("AxPickaxes".equals(name)) loadAxPickIfReady();
        }
    }
}
