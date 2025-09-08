package com.bitaspire.cyberlevels.level;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Represents a level system with various configurations and functionalities.
 *
 * <p> This interface provides methods to manage levels, experience points, formulas,
 * leaderboards, anti-abuse mechanisms, and placeholders.
 *
 * @param <N> the numeric type used for experience points and calculations
 */
public interface LevelSystem<N extends Number> {

    /**
     * Gets the starting level for the level system.
     * @return the starting level
     */
    long getStartLevel();

    /**
     * Gets the starting experience points for the level system.
     * @return the starting experience points
     */
    int getStartExp();

    /**
     * Gets the maximum level for the level system.
     * @return the maximum level
     */
    long getMaxLevel();

    /**
     * Gets the operator used for calculations in the level system.
     * @return the operator
     */
    @NotNull
    Operator<N> getOperator();

    /**
     * Gets a set of all levels defined in the level system.
     * @return a set of levels
     */
    @NotNull
    Set<Level<N>> getLevels();

    /**
     * Gets the level corresponding to the specified level number.
     * @param level the level number
     * @return the corresponding Level object
     */
    Level<N> getLevel(long level);

    /**
     * Gets the default formula used for experience calculations.
     * @return the default formula
     */
    @NotNull
    Formula<N> getFormula();

    /**
     * Gets a custom formula for the specified level.
     *
     * @param level the level number
     * @return the custom formula for the specified level, or null if no custom formula is defined
     */
    Formula<N> getCustomFormula(long level);

    /**
     * Gets the leaderboard associated with the level system.
     * @return the leaderboard
     */
    @NotNull
    Leaderboard<N> getLeaderboard();

    /**
     * Gets a map of experience sources defined in the level system.
     * @return a map of experience sources
     */
    @NotNull
    Map<String, ExpSource> getExpSources();

    /**
     * Gets a map of anti-abuse mechanisms defined in the level system.
     * @return a map of anti-abuse mechanisms
     */
    @NotNull
    Map<String, AntiAbuse> getAntiAbuses();

    /**
     * Checks if the specified player is limited by any anti-abuse mechanism for the given experience source.
     *
     * @param player the player to check
     * @param source the experience source
     *
     * @return true if the player is limited, false otherwise
     */
    default boolean checkAntiAbuse(Player player, ExpSource source) {
        for (AntiAbuse abuse : getAntiAbuses().values())
            if (abuse.isLimited(player, source)) return true;

        return false;
    }

    /**
     * Rounds the given amount of experience points according to the level system's rules.
     *
     * @param amount the amount of experience points to round
     * @return the rounded amount of experience points
     */
    @NotNull
    N roundDecimal(N amount);

    /**
     * Rounds the given amount of experience points and returns it as a string representation.
     *
     * @param amount the amount of experience points to round
     * @return the rounded amount of experience points as a string
     */
    @NotNull
    String roundDecimalAsString(N amount);

    /**
     * Generates a progress bar string representing the player's progress towards the next level.
     *
     * @param exp the current experience points of the player
     * @param requiredExp the experience points required to reach the next level
     *
     * @return a string representing the progress bar
     */
    @NotNull
    String getProgressBar(N exp, N requiredExp);

    /**
     * Calculates the percentage of experience points the player has towards the next level.
     *
     * @param exp the current experience points of the player
     * @param requiredExp the experience points required to reach the next level
     *
     * @return a string representing the percentage of experience points
     */
    @NotNull
    String getPercent(N exp, N requiredExp);

    /**
     * Replaces placeholders in the given string with actual values based on the player's data.
     *
     * @param string the string containing placeholders
     * @param player the player whose data will be used for replacement
     *
     * @return the string with placeholders replaced by actual values
     */
    @NotNull
    String replacePlaceholders(String string, Player player, boolean safeForFormula);
}
