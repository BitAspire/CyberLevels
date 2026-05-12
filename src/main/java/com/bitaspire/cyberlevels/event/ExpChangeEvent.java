package com.bitaspire.cyberlevels.event;

import com.bitaspire.cyberlevels.user.LevelUser;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when CyberLevels is about to apply an EXP change to a user.
 *
 * <p>The event exposes both the previous and projected EXP/level values so listeners can inspect
 * the full transition. The mutable {@link #expAmount} represents the delta that will actually be
 * applied and may be adjusted by listeners before the change is finalized.
 */
@Getter
public class ExpChangeEvent extends Event {

    private static final HandlerList handlerList = new HandlerList();

    /**
     * User whose EXP is being modified.
     */
    private final LevelUser<?> user;
    /**
     * EXP value before the pending change is applied.
     */
    private final double oldExp, newExp;
    /**
     * Level value before and after the pending change is applied.
     */
    private final double oldLevel, newLevel;
    /**
     * Mutable delta that CyberLevels intends to apply.
     */
    @Setter
    private double expAmount;

    /**
     * Creates a new EXP change event snapshot.
     *
     * @param user affected user
     * @param oldExp EXP before the change
     * @param oldLevel level before the change
     * @param newExp projected EXP after the change
     * @param newLevel projected level after the change
     * @param expAmount mutable EXP delta that will be applied
     */
    public ExpChangeEvent(LevelUser<?> user, double oldExp, double oldLevel, double newExp, double newLevel, double expAmount) {
        super(!Bukkit.isPrimaryThread());

        this.user = user;
        this.oldExp = oldExp;
        this.oldLevel = oldLevel;
        this.newExp = newExp;
        this.newLevel = newLevel;
        this.expAmount = expAmount;
    }

    /**
     * Dispatches this event through Bukkit's plugin manager.
     *
     * <p>This helper exists so internal callers can create and emit the event in one fluent step.
     */
    public void call() {
        Bukkit.getPluginManager().callEvent(this);
    }

    /**
     * Returns the live Bukkit player for integrations that expect a direct player method.
     *
     * <p>This is an alias for {@code getUser().getPlayer()} and is especially useful for
     * reflection-based hooks that cannot consume CyberLevels' {@link LevelUser} wrapper directly.
     *
     * @return affected online player
     */
    @NotNull
    public Player getPlayer() {
        return user.getPlayer();
    }

    /**
     * Legacy-style alias for the mutable EXP delta.
     *
     * @return EXP amount that will be applied
     */
    public double getAmount() {
        return expAmount;
    }

    /**
     * Legacy-style alias for changing the mutable EXP delta.
     *
     * @param amount replacement EXP amount
     */
    public void setAmount(double amount) {
        this.expAmount = amount;
    }

    /**
     * Legacy-style alias for the previous EXP value.
     *
     * @return EXP before the change
     */
    public double getOldXP() {
        return oldExp;
    }

    /**
     * Legacy-style alias for the projected EXP value.
     *
     * @return EXP after the change preview
     */
    public double getNewXP() {
        return newExp;
    }

    /**
     * Returns the Bukkit handler list for this event type.
     *
     * @return static handler list required by the Bukkit event contract
     */
    public static HandlerList getHandlerList() {
        return handlerList;
    }

    /**
     * Returns the Bukkit handler list for this event type.
     *
     * @return handler list required by the Bukkit event contract
     */
    @NotNull
    public HandlerList getHandlers() {
        return handlerList;
    }
}
