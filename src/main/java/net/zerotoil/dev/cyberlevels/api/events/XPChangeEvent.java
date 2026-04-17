package net.zerotoil.dev.cyberlevels.api.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class XPChangeEvent extends Event {
    private static final HandlerList handlerList = new HandlerList();
    private final Player player;
    private final double oldXP;
    private final double newXP;
    private double amount;

    public XPChangeEvent(@NotNull Player player, double oldXP, double newXP, double amount) {
        super(!Bukkit.isPrimaryThread());

        this.player = player;
        this.oldXP = oldXP;
        this.newXP = newXP;
        this.amount = amount;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    public static HandlerList getHandlerList() {
        return handlerList;
    }

    public Player getPlayer() {
        return player;
    }

    public double getOldXP() {
        return oldXP;
    }

    public double getNewXP() {
        return newXP;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
