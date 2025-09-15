package com.bitaspire.cyberlevels.level;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a source of experience points in a leveling system.
 *
 * <p> This interface provides methods to manage experience sources, including
 * categories, names, intervals, ranges, inclusion/exclusion lists, permissions,
 * and experience calculations.
 */
public interface ExpSource {

    /**
     * Gets the category of the experience source.
     * @return the category as a non-null string
     */
    @NotNull
    String getCategory();

    /**
     * Gets the name of the experience source.
     * @return the name as a non-null string
     */
    @NotNull
    String getName();

    /**
     * Checks if the experience source is enabled.
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Gets the interval for the experience source.
     * @return the interval as an integer
     */
    int getInterval();

    /**
     * Gets the range of experience points for the source.
     * @return a non-null Range object representing the experience range
     */
    @NotNull
    Range getRange();

    /**
     * Checks if the experience source includes specific items or actions.
     * @return true if it includes, false otherwise
     */
    boolean includes();

    /**
     * Checks if the experience source operates as a whitelist.
     * @return true if it is a whitelist, false if it is a blacklist
     */
    boolean isWhitelist();

    /**
     * Gets the list of items or actions included in the experience source.
     * @return a non-null list of strings representing the included items/actions
     */
    @NotNull
    List<String> getIncludeList();

    /**
     * Checks if the experience source uses specific values.
     * @return true if it uses specific values, false otherwise
     */
    boolean useSpecifics();

    /**
     * Gets the list of specific values used by the experience source.
     * @return a non-null list of strings representing the specific values
     */
    @NotNull
    List<String> getSpecificList();

    /**
     * Checks if a given value is in the include/exclude list of the experience source.
     *
     * @param value the value to check
     * @param specific whether to check in the specific list
     * @return true if the value is in the list, false otherwise
     */
    boolean isInList(String value, boolean specific);

    /**
     * Checks if a given value is in the include/exclude list of the experience source.
     *
     * @param value the value to check
     * @return true if the value is in the list, false otherwise
     */
    default boolean isInList(String value) {
        return isInList(value, false);
    }

    /**
     * Checks if a player has permission for the experience source.
     *
     * @param player the player to check
     * @param specific whether to check for specific permissions
     *
     * @return true if the player has permission, false otherwise
     */
    boolean hasPermission(Player player, boolean specific);

    /**
     * Checks if a player has permission for the experience source.
     *
     * @param player the player to check
     * @return true if the player has permission, false otherwise
     */
    default boolean hasPermission(Player player) {
        return hasPermission(player, false);
    }

    /**
     * Gets a specific range of experience points based on a given value.
     *
     * @param value the value to determine the specific range
     * @return a Range object representing the specific experience range
     */
    Range getSpecificRange(String value);

    /**
     * Calculates the experience points for a given string input.
     *
     * @param string the input string to calculate experience from
     * @return the calculated experience points as a double
     */
    double getPartialMatchesExp(String string);

    /**
     * Gets the registrable object associated with the experience source.
     * @return a non-null Registrable object
     */
    @NotNull
    Registrable getRegistrable();

    /**
     * Represents a numerical range with minimum and maximum values,
     * and provides a method to get a random value within that range.
     */
    interface Range {

        /**
         * Gets the minimum value of the range.
         * @return the minimum value as a double
         */
        double getMin();

        /**
         * Gets the maximum value of the range.
         * @return the maximum value as a double
         */
        double getMax();

        /**
         * Gets a random value within the range.
         * @return a random double value between min and max
         */
        double getRandom();
    }

    /**
     * Represents an object that can be registered and unregistered,
     * typically for event handling or similar purposes.
     */
    interface Registrable {

        /**
         * Registers the object, enabling its functionality.
         */
        void register();

        /**
         * Unregisters the object, disabling its functionality.
         */
        void unregister();
    }
}
