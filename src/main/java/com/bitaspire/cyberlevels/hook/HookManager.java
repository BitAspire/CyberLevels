package com.bitaspire.cyberlevels.hook;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.level.ExpSource;
import com.bitaspire.cyberlevels.user.LevelUser;
import net.zerotoil.dev.cybercore.addons.Metrics;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class HookManager {

    private final Set<Hook> hooks = new HashSet<>();
    private final CyberLevels main;

    public HookManager(CyberLevels main) {
        (this.main = main).logger("&dLoading plugin hooks...");

        long startTime = System.currentTimeMillis();
        new Metrics(main, 13782);

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

        if (source.useSpecifics()) {
            if (source.isInList(item, true)) counter = source.getSpecificRange(item).getRandom();
        }
        else if (source.isEnabled()) {
            if (source.isInList(item)) counter = source.getRange().getRandom();
        }

        if (counter == 0) return;

        LevelUser<?> user = main.userManager().getUser(player);
        if (counter > 0) {
            user.addExp(counter + "", main.cache().config().isMultiplierEvents());
            return;
        }

        user.removeExp(Math.abs(counter) + "");
    }

    public void register() {
        hooks.forEach(Hook::register);
    }

    public void unregister() {
        hooks.forEach(Hook::unregister);
        hooks.clear();
    }
}
