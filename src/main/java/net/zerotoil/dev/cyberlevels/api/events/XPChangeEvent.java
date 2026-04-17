package net.zerotoil.dev.cyberlevels.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link com.bitaspire.cyberlevels.event.ExpChangeEvent} instead.
 */
@Deprecated
@Getter
public class XPChangeEvent extends Event {

    private static final HandlerList handlerList = new HandlerList();

    private final Player player;
    private final double oldXP;
    @Setter
    private double amount;

    public XPChangeEvent(@NotNull Player player, double oldXP, double amount) {
        super(!Bukkit.isPrimaryThread());

        this.player = player;
        this.oldXP = oldXP;
        this.amount = amount;
    }

    public static HandlerList getHandlerList() {
        return handlerList;
    }

    /**
     * EXP after this gain if the current {@link #getAmount()} were applied in one step (informational;
     * level-ups are still processed afterward by CyberLevels). Updates when {@link #setAmount(double)} runs.
     */
    public double getNewXP() {
        return oldXP + amount;
    }

    @NotNull
    public HandlerList getHandlers() {
        return handlerList;
    }
}
