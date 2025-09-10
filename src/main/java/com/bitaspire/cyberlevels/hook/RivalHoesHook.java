package com.bitaspire.cyberlevels.hook;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.cache.AntiAbuse;
import me.rivaldev.harvesterhoes.api.events.RivalBlockBreakEvent;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

final class RivalHoesHook implements Hook, Listener {

    private final CyberLevels main;
    private final HookManager manager;

    RivalHoesHook(CyberLevels main, HookManager manager) {
        this.main = main;
        this.manager = manager;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    private void onRivalBlockBreak(RivalBlockBreakEvent event) {
        if (event.isCancelled()) return;

        AntiAbuse antiAbuse = main.cache().antiAbuse();
        Player player = event.getPlayer();

        if (antiAbuse.isSilkTouchEnabled() &&
                (main.serverVersion() > 8
                        ? player.getInventory().getItemInMainHand()
                        : player.getItemInHand())
                        .containsEnchantment(Enchantment.SILK_TOUCH))
            return;

        Block block = event.getCrop();
        if (antiAbuse.onlyNaturalBlocks() && block.hasMetadata("CLV_PLACED")) {
            if (!(block.getBlockData() instanceof Ageable) || antiAbuse.includeNaturalCrops())
                return;

            Ageable ageable = (Ageable) block.getBlockData();
            if (ageable.getAge() != ageable.getMaximumAge()) return;
        }

        manager.sendExp(
                player, main.cache().earnExp().getExpSources().get("rivalhh-breaking"),
                block.getType().toString()
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
