package com.bitaspire.cyberlevels.level;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents a mathematical formula used for calculating experience points or levels.
 *
 * <p> This interface provides methods to retrieve the formula as a string and to evaluate
 * the formula based on a player's attributes.
 *
 * @param <N> the numeric type used for calculations
 */
public interface Formula<N extends Number> {

    /**
     * Retrieves the formula as a string representation.
     * @return the formula in string format
     */
    @NotNull
    String getAsString();

    /**
     * Evaluates the formula based on the provided player's attributes.
     *
     * @param player the player whose attributes are used for evaluation
     * @return the result of the formula evaluation
     */
    @NotNull
    N evaluate(UUID uuid);
}
