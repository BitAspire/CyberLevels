package com.bitaspire.cyberlevels.listener;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.utility.SpigotUpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bundles the core Bukkit listeners used by CyberLevels.
 *
 * <p>This class groups the plugin's always-on listeners, such as player load/save hooks and the
 * piston metadata fix used by the anti-abuse system. The actual listener instances are created in
 * the constructor and then registered or unregistered as one unit during runtime startup and
 * shutdown.
 */
public class Listeners {

    private final Set<ExpListener> listeners = new HashSet<>();
    private final CyberLevels main;

    /**
     * Creates the listener bundle for the current plugin runtime.
     *
     * @param main owning plugin instance
     */
    public Listeners(CyberLevels main) {
        this.main = main;

        new ExpListener() {
            @EventHandler
            private void onJoin(PlayerJoinEvent event) {
                main.userManager().loadPlayer(event.getPlayer());
                SpigotUpdateChecker.deliverPendingOpChatOnJoin(main, event.getPlayer());
            }

            @EventHandler
            private void onLeave(PlayerQuitEvent event) {
                main.userManager().savePlayer(event.getPlayer(), true);
            }
        };

        new ExpListener() {
            @EventHandler
            private void onPistonExtend(BlockPistonExtendEvent event) {
                if (!event.isCancelled()) fixPlacedAbuse(event.getBlocks(), event.getDirection());
            }

            @EventHandler
            private void onPistonRetract(BlockPistonRetractEvent event) {
                if (!event.isCancelled()) fixPlacedAbuse(event.getBlocks(), event.getDirection());
            }
        };

    }

    /**
     * Registers every bundled listener with Bukkit.
     */
    public void register() {
        listeners.forEach(ExpListener::register);
    }

    /**
     * Unregisters every bundled listener from Bukkit.
     */
    public void unregister() {
        listeners.forEach(ExpListener::unregister);
    }

    private void fixPlacedAbuse(List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (!block.hasMetadata("CLV_PLACED")) continue;

            main.scheduler().runTaskLater(() ->
                    block.getRelative(direction).setMetadata(
                            "CLV_PLACED",
                            new FixedMetadataValue(main, true)
                    ), 1L);
        }
    }

    private class ExpListener implements Listener {

        ExpListener() {
            listeners.add(this);
        }

        void register() {
            Bukkit.getPluginManager().registerEvents(this, main);
        }

        void unregister() {
            HandlerList.unregisterAll(this);
        }
    }
}
