package com.bitaspire.cyberlevels.level;

import com.bitaspire.cyberlevels.user.LevelUser;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a level within a level system, including its properties and behaviors.
 *
 * <p> This interface provides methods to access level information, associated formulas,
 * rewards, and experience requirements.
 *
 * @param <N> the numeric type used for experience points and calculations
 */
public interface Level<N extends Number> {

    /**
     * Gets the numeric level value.
     * @return the level number
     */
    long getLevel();

    /**
     * Gets the formula associated with this level.
     * @return the formula
     */
    @NotNull
    Formula<N> getFormula();

    /**
     * Gets a list of rewards associated with this level.
     * @return a list of rewards
     */
    @NotNull
    List<Reward> getRewards();

    /**
     * Adds a reward to this level.
     * @param reward the reward to add
     */
    void addReward(Reward reward);

    /**
     * Clears all rewards associated with this level.
     */
    void clearRewards();

    /**
     * Gets the required experience points for a specific user to reach this level.
     * @param user the level user
     * @return the required experience points
     */
    N getRequiredExp(LevelUser<N> user);

    /**
     * Gets the required experience points for a specific player to reach this level.
     * @param player the player
     * @return the required experience points
     */
    N getRequiredExp(Player player);
}
