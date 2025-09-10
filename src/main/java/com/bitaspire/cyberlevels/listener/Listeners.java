package com.bitaspire.cyberlevels.listener;

import com.bitaspire.cyberlevels.CyberLevels;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Listeners {

    private final Set<ExpListener> listeners = new HashSet<>();
    private final CyberLevels main;

    public Listeners(CyberLevels main) {
        this.main = main;

        new ExpListener() {
            @EventHandler
            private void onJoin(PlayerJoinEvent event) {
                main.userManager().loadPlayer(event.getPlayer());
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

        register();
    }

    public void register() {
        listeners.forEach(ExpListener::register);
    }

    public void unregister() {
        listeners.forEach(ExpListener::unregister);
    }

    private void fixPlacedAbuse(List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (block.hasMetadata("CLV_PLACED")) {
                (new BukkitRunnable() {
                    @Override
                    public void run() {
                        Block newBlock = block.getRelative(direction);
                        newBlock.setMetadata("CLV_PLACED", new FixedMetadataValue(main, true));
                    }
                }).runTaskLater(main, 1L);
            }
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
