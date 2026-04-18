package net.zerotoil.dev.cyberlevels.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Legacy compatibility event preserved for older integrations.
 *
 * <p>This event mirrors the historical API exposed by earlier CyberLevels releases. New code should
 * migrate to {@link com.bitaspire.cyberlevels.event.ExpChangeEvent}, which exposes richer context
 * such as old/new levels and the resolved {@code LevelUser}.
 *
 * @deprecated Use {@link com.bitaspire.cyberlevels.event.ExpChangeEvent} instead.
 */
@Deprecated
@Getter
public class XPChangeEvent extends Event {

    private static final HandlerList handlerList = new HandlerList();

    /**
     * Player whose EXP is being modified.
     */
    private final Player player;
    /**
     * EXP value before the pending change is applied.
     */
    private final double oldXP;
    /**
     * Mutable EXP delta that legacy listeners may adjust.
     */
    @Setter
    private double amount;

    /**
     * Creates a legacy EXP change event snapshot.
     *
     * @param player affected player
     * @param oldXP EXP value before the change
     * @param amount mutable EXP delta that will be applied
     */
    public XPChangeEvent(@NotNull Player player, double oldXP, double amount) {
        super(!Bukkit.isPrimaryThread());

        this.player = player;
        this.oldXP = oldXP;
        this.amount = amount;
    }

    /**
     * Returns the Bukkit handler list for this legacy event type.
     *
     * @return handler list required by the Bukkit event contract
     */
    public static HandlerList getHandlerList() {
        return handlerList;
    }

    /**
     * Returns the projected EXP after applying the current delta in one step.
     *
     * <p>This value is informational only. CyberLevels may still perform additional level-up logic
     * after the delta is processed.
     *
     * @return projected EXP value based on the current {@link #getAmount()}
     */
    public double getNewXP() {
        return oldXP + amount;
    }

    /**
     * Returns the Bukkit handler list for this event instance.
     *
     * @return handler list required by the Bukkit event contract
     */
    @NotNull
    public HandlerList getHandlers() {
        return handlerList;
    }
}
