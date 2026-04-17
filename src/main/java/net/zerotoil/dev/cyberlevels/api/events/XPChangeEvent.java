package net.zerotoil.dev.cyberlevels.api.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before positive EXP from CyberLevels is applied. Listeners may adjust {@link #setAmount(double)}.
 * {@link #getNewXP()} always reflects {@link #getOldXP()} plus the current {@link #getAmount()}.
 */
public class XPChangeEvent extends Event {
    private static final HandlerList handlerList = new HandlerList();
    private final Player player;
    private final double oldXP;
    private double amount;

    public XPChangeEvent(@NotNull Player player, double oldXP, double amount) {
        super(!Bukkit.isPrimaryThread());

        this.player = player;
        this.oldXP = oldXP;
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

    /**
     * EXP after this gain if the current {@link #getAmount()} were applied in one step (informational;
     * level-ups are still processed afterward by CyberLevels). Updates when {@link #setAmount(double)} runs.
     */
    public double getNewXP() {
        return oldXP + amount;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
