package com.bitaspire.cyberlevels.level;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents a compiled formula used by the level system to calculate required experience.
 *
 * <p>Implementations usually wrap a raw expression from {@code levels.yml}, optionally enriched
 * with player-specific placeholders, and expose both the original string form and the evaluated
 * numeric result.
 *
 * @param <N> numeric type produced by the active level engine
 */
public interface Formula<N extends Number> {

    /**
     * Returns the original textual expression used to build this formula.
     *
     * @return expression string as configured by the plugin
     */
    @NotNull
    String getAsString();

    /**
     * Evaluates the formula for a specific player context.
     *
     * <p>The supplied UUID allows implementations to resolve per-player placeholders before
     * performing the final numeric evaluation.
     *
     * @param uuid player UUID whose data should be used during evaluation
     * @return evaluated numeric result
     */
    @NotNull
    N evaluate(UUID uuid);
}
