package com.bitaspire.cyberlevels.level;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes one configurable source of EXP gains or losses.
 *
 * <p>An EXP source corresponds to an entry from {@code earn-exp.yml}. Depending on configuration,
 * a source can represent a flat reward range, a permission-based source, or a map of specific
 * values such as block states, item names, players, or entity types.
 */
public interface ExpSource {

    /**
     * Returns the unique configuration category of this source.
     *
     * @return source category id
     */
    @NotNull
    String getCategory();

    /**
     * Returns the specific subsection name used by this source, when applicable.
     *
     * @return specific subsection identifier, or an empty string when unused
     */
    @NotNull
    String getName();

    /**
     * Indicates whether the source's general reward mode is enabled.
     *
     * @return {@code true} when the base range is active
     */
    boolean isEnabled();

    /**
     * Returns the configured interval for timer-based sources.
     *
     * @return source interval in seconds or ticks depending on the implementation context
     */
    int getInterval();

    /**
     * Returns the general reward range for this source.
     *
     * @return numeric range used when the source is not in specific mode
     */
    @NotNull
    Range getRange();

    /**
     * Indicates whether the source uses an include/exclude list.
     *
     * @return {@code true} when include-list filtering is enabled
     */
    boolean includes();

    /**
     * Indicates how the include list should be interpreted.
     *
     * @return {@code true} when the include list acts as a whitelist, otherwise a blacklist
     */
    boolean isWhitelist();

    /**
     * Returns the configured include-list entries for this source.
     *
     * @return include-list values in their configured order
     */
    @NotNull
    List<String> getIncludeList();

    /**
     * Indicates whether the source uses a specific-value map instead of a single general range.
     *
     * @return {@code true} when specific values are enabled
     */
    boolean useSpecifics();

    /**
     * Returns the configured specific keys for this source.
     *
     * @return specific map keys available for matching
     */
    @NotNull
    List<String> getSpecificList();

    /**
     * Checks whether a runtime value is accepted by this source.
     *
     * @param value runtime value to evaluate
     * @param specific whether the lookup should use the specific-value map instead of the general
     *        include/exclude list
     * @return {@code true} when the value matches the requested lookup mode
     */
    boolean isInList(String value, boolean specific);

    /**
     * Resolves which configured specific key should be used for a runtime value.
     *
     * <p>Implementations may return exact matches, compatibility fallbacks, or {@code null} when no
     * specific key is suitable.
     *
     * @param value runtime token such as a material, block-state key, player name, or entity type
     * @return matching configured key, or {@code null} when none applies
     */
    @Nullable
    default String matchSpecificKey(String value) {
        return null;
    }

    /**
     * Convenience overload for checking the general include/exclude rules of this source.
     *
     * @param value runtime value to evaluate
     * @return {@code true} when the value matches the general include rules
     */
    default boolean isInList(String value) {
        return isInList(value, false);
    }

    /**
     * Checks whether a player satisfies the source's permission rules.
     *
     * @param player player to evaluate
     * @param specific whether to test the specific-value permission map instead of the general
     *        include list
     * @return {@code true} when the player matches the configured permission logic
     */
    boolean hasPermission(Player player, boolean specific);

    /**
     * Convenience overload for checking the source's general permission rules.
     *
     * @param player player to evaluate
     * @return {@code true} when the player matches the general permission logic
     */
    default boolean hasPermission(Player player) {
        return hasPermission(player, false);
    }

    /**
     * Returns the reward range associated with a specific configured key.
     *
     * @param value specific key to resolve
     * @return reward range for that key
     */
    Range getSpecificRange(String value);

    /**
     * Calculates EXP based on a partial string match operation.
     *
     * <p>This is primarily used by free-form sources such as chat, enchanting, and brewing where
     * one runtime string may match multiple configured fragments.
     *
     * @param string runtime text to inspect
     * @return resulting EXP amount after partial-match evaluation
     */
    double getPartialMatchesExp(String string);

    /**
     * Returns the lifecycle adapter used to register and unregister this source at runtime.
     *
     * @return registrable responsible for activating the source
     */
    @NotNull
    Registrable getRegistrable();

    /**
     * Represents a numeric reward range.
     *
     * <p>Implementations may represent a fixed value, a min/max interval, or any custom random
     * generation strategy compatible with CyberLevels.
     */
    interface Range {

        /**
         * Returns the minimum possible value of the range.
         *
         * @return lower bound
         */
        double getMin();

        /**
         * Returns the maximum possible value of the range.
         *
         * @return upper bound
         */
        double getMax();

        /**
         * Produces a value from the range according to the implementation's selection rules.
         *
         * @return generated reward value
         */
        double getRandom();
    }

    /**
     * Lifecycle adapter used to activate and deactivate an EXP source.
     *
     * <p>This abstraction lets sources register Bukkit listeners, scheduler tasks, or any other
     * runtime resource without exposing those details to the outer cache.
     */
    interface Registrable {

        /**
         * Activates the source and allocates any required runtime resources.
         */
        void register();

        /**
         * Deactivates the source and releases any runtime resources it owns.
         */
        void unregister();
    }
}
