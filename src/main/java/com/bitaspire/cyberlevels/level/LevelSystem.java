package com.bitaspire.cyberlevels.level;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central API for the active CyberLevels progression engine.
 *
 * <p>The level system ties together numeric operations, required-EXP formulas, leaderboard
 * calculation, anti-abuse checks, placeholder replacement, and formatting helpers. Most plugin and
 * integration code should interact with this interface rather than with a concrete implementation.
 *
 * @param <N> numeric type used by the active level engine
 */
public interface LevelSystem<N extends Number> {

    /**
     * Returns the configured starting level assigned to newly created users.
     *
     * @return first level in the progression curve
     */
    long getStartLevel();

    /**
     * Returns the configured starting EXP assigned to newly created users.
     *
     * @return starting EXP value
     */
    int getStartExp();

    /**
     * Returns the highest level reachable through this progression system.
     *
     * @return configured max level
     */
    long getMaxLevel();

    /**
     * Returns the numeric operator used by this engine.
     *
     * @return arithmetic abstraction for the active numeric backend
     */
    @NotNull
    Operator<N> getOperator();

    /**
     * Returns the fallback formula used when no per-level override exists.
     *
     * @return default required-EXP formula
     */
    @NotNull
    Formula<N> getFormula();

    /**
     * Returns the custom formula override for a specific level, if one exists.
     *
     * @param level level whose override should be resolved
     * @return override formula, or {@code null} when the default formula should be used
     */
    Formula<N> getCustomFormula(long level);

    /**
     * Calculates the required EXP for a specific level and player context.
     *
     * @param level level whose requirement should be evaluated
     * @param uuid player UUID used for placeholder-aware formulas
     * @return required EXP for the supplied level
     */
    @NotNull
    N getRequiredExp(long level, UUID uuid);

    /**
     * Returns every reward configured for the supplied level.
     *
     * @param level level whose rewards should be retrieved
     * @return ordered list of rewards for that level
     */
    @NotNull
    List<Reward> getRewards(long level);

    /**
     * Returns the leaderboard maintained by this level system.
     *
     * @return active leaderboard instance
     */
    @NotNull
    Leaderboard<N> getLeaderboard();

    /**
     * Returns the configured EXP sources available to the level system.
     *
     * @return EXP sources keyed by configuration id
     */
    @NotNull
    Map<String, ExpSource> getExpSources();

    /**
     * Returns the configured anti-abuse modules currently enforced by the level system.
     *
     * @return anti-abuse modules keyed by configuration id
     */
    @NotNull
    Map<String, AntiAbuse> getAntiAbuses();

    /**
     * Checks every configured anti-abuse module for a possible restriction.
     *
     * @param player player attempting to gain or lose EXP
     * @param source EXP source being processed
     * @return {@code true} when any anti-abuse module blocks the action
     */
    default boolean checkAntiAbuse(Player player, ExpSource source) {
        for (AntiAbuse abuse : getAntiAbuses().values())
            if (abuse.isLimited(player, source)) return true;

        return false;
    }

    /**
     * Applies the engine's configured rounding rules to a numeric value.
     *
     * @param amount numeric value to round
     * @return rounded value in the engine's native number type
     */
    @NotNull
    N round(N amount);

    /**
     * Applies rounding and formats the result as text.
     *
     * @param amount numeric value to round and format
     * @return rounded value rendered as a string
     */
    @NotNull
    String roundString(N amount);

    /**
     * Applies rounding and returns the result as a primitive {@code double}.
     *
     * @param amount numeric value to round
     * @return rounded value converted to {@code double}
     */
    double roundDouble(N amount);

    /**
     * Formats an arbitrary numeric value using the same rules applied to player EXP values.
     *
     * @param value numeric value to format
     * @return formatted number string
     */
    @NotNull
    String formatNumber(Number value);

    /**
     * Builds the configured progress bar for a pair of current and required EXP values.
     *
     * @param exp current EXP value
     * @param requiredExp EXP required for the next level
     * @return formatted progress bar string
     */
    @NotNull
    String getProgressBar(N exp, N requiredExp);

    /**
     * Calculates a formatted completion percentage for the supplied EXP values.
     *
     * @param exp current EXP value
     * @param requiredExp EXP required for the next level
     * @return percentage string ready for display
     */
    @NotNull
    String getPercent(N exp, N requiredExp);

    /**
     * Replaces CyberLevels placeholders inside a string using player data and system state.
     *
     * @param string source string containing placeholders
     * @param uuid player UUID used for lookup context
     * @param safeForFormula whether replacements should avoid formatting that could break formula
     *        evaluation
     * @return string with placeholders resolved
     */
    @NotNull
    String replacePlaceholders(String string, UUID uuid, boolean safeForFormula);
}
