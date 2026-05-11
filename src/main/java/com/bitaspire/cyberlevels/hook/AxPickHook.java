package com.bitaspire.cyberlevels.hook;

import com.artillexstudios.axpickaxes.api.events.PlayerXPGainEvent;
import com.bitaspire.cyberlevels.CyberLevels;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/**
 * AxPickaxes integration.
 *
 * <p>AxPickaxes cancels the vanilla {@link org.bukkit.event.block.BlockBreakEvent} for tool-managed
 * breaks and runs its own drop/XP pipeline, which makes a {@code BlockBreakEvent} listener
 * unreliable as a trigger. Instead we listen to AxPickaxes' own {@link PlayerXPGainEvent}, which
 * is fired once per successful break, and dispatch a single roll of the {@code axpick-breaking}
 * earn-exp source. The event carries no block reference, so per-block include/specific lists are
 * ignored — configure {@code general.exp} to set the flat reward.
 */
final class AxPickHook implements Hook, Listener {

    private final CyberLevels main;
    private final HookManager manager;

    AxPickHook(CyberLevels main, HookManager manager) {
        this.main = main;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerXPGain(PlayerXPGainEvent event) {
        manager.sendExp(
                event.getPlayer(),
                main.cache().earnExp().getExpSources().get("axpick-breaking"),
                ""
        );
    }

    @Override
    public void register() {
        main.getServer().getPluginManager().registerEvents(this, main);
    }

    @Override
    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
