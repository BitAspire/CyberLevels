package com.bitaspire.cyberlevels.hook;

import com.artillexstudios.axboosters.api.AxBoostersAPI;
import com.artillexstudios.axboosters.api.events.AxBoostersLoadEvent;
import com.artillexstudios.axboosters.hooks.booster.BoosterHook;
import com.artillexstudios.axboosters.users.User;
import com.artillexstudios.axboosters.users.UserList;
import com.bitaspire.cyberlevels.CyberLevels;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

final class AxBoostersHook implements Hook, BoosterHook, Listener {

    private static final Key KEY = Key.key("cyberlevels2", "xp");

    private static volatile AxBoostersHook registered;

    private final CyberLevels main;

    AxBoostersHook(CyberLevels main) {
        this.main = main;
    }

    @Override
    public Key getKey() {
        return KEY;
    }

    @Override
    public Material getIcon() {
        return Material.EXPERIENCE_BOTTLE;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    double getMultiplier(Player player) {
        if (player == null) return 1D;

        try {
            User user = UserList.getUser(player);
            if (user == null) return 1D;

            float boost = user.getBoost(this);
            if (Float.isNaN(boost) || boost <= 0F) return 1D;
            return boost;
        } catch (Throwable t) {
            return 1D;
        }
    }

    @EventHandler
    public void onAxBoostersLoad(AxBoostersLoadEvent event) {
        synchronized (AxBoostersHook.class) {
            if (registered != null) return;
            try {
                AxBoostersAPI.registerBoosterHook(main, this);
                registered = this;
            } catch (Throwable t) {
                main.logger("&cFailed to register AxBoosters hook: " + t.getMessage());
            }
        }
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
