package com.bitaspire.cyberlevels.event;

import com.bitaspire.cyberlevels.user.LevelUser;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class ExpChangeEvent extends Event {

    @Getter
    private static final HandlerList handlerList = new HandlerList();

    private final LevelUser<?> user;
    private final double oldExp, newExp;
    private final double oldLevel, newLevel;
    @Setter
    private double expAmount;

    public ExpChangeEvent(LevelUser<?> user, double oldExp, double oldLevel, double newExp, double newLevel, double expAmount) {
        super(!Bukkit.isPrimaryThread());

        this.user = user;
        this.oldExp = oldExp;
        this.oldLevel = oldLevel;
        this.newExp = newExp;
        this.newLevel = newLevel;
        this.expAmount = expAmount;
    }

    public void call() {
        Bukkit.getPluginManager().callEvent(this);
    }

    @NotNull
    public HandlerList getHandlers() {
        return handlerList;
    }
}
