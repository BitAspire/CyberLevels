package com.bitaspire.cyberlevels.user;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents a user in the leveling system, providing methods to manage levels and experience points.
 *
 * <p> This interface includes functionalities for retrieving player information, managing levels,
 * experience points, and checking permissions.
 *
 * @param <N> the numeric type used for experience points and calculations
 */
public interface LevelUser<N extends Number> extends Comparable<LevelUser<N>> {

    /**
     * Gets the UUID of the user.
     * @return the UUID
     */
    @NotNull
    UUID getUuid();

    /**
     * Gets the OfflinePlayer object associated with this user.
     * @return the OfflinePlayer object
     */
    OfflinePlayer getOffline();

    /**
     * Gets the Player object associated with this user.
     * @return the Player object
     */
    @NotNull
    Player getPlayer();

    @NotNull
    String getName();

    boolean isOnline();

    /**
     * Gets the current level of the user.
     * @return the current level
     */
    long getLevel();

    /**
     * Adds levels to the user's current level.
     * @param amount the number of levels to add
     */
    void addLevel(long amount);

    /**
     * Sets the user's level to a specific value.
     * @param amount the level to set
     * @param sendMessage whether to send a message to the user about the level change
     */
    void setLevel(long amount, boolean sendMessage);

    /**
     * Removes levels from the user's current level.
     * @param amount the number of levels to remove
     */
    void removeLevel(long amount);

    /**
     * Gets the current experience points of the user.
     * @return the current experience points
     */
    @NotNull
    N getExp();

    /**
     * Gets the experience points required for the user to reach the next level.
     * @return the experience points required for the next level
     */
    @NotNull
    N getRequiredExp();

    /**
     * Gets the remaining experience points needed for the user to reach the next level.
     * @return the remaining experience points needed
     */
    @NotNull
    N getRemainingExp();

    /**
     * Gets the percentage of experience points the user has towards the next level.
     * @return the percentage as a string
     */
    @NotNull
    String getPercent();

    /**
     * Gets the progress bar representing the user's experience towards the next level.
     * @return the progress bar as a string
     */
    @NotNull
    String getProgressBar();

    /**
     * Adds experience points to the user.
     *
     * @param amount the amount of experience points to add
     * @param multiply whether to apply the user's multiplier to the added experience
     */
    void addExp(N amount, boolean multiply);

    /**
     * Adds experience points to the user.
     *
     * @param amount the amount of experience points to add, as a string
     * @param multiply whether to apply the user's multiplier to the added experience
     */
    void addExp(String amount, boolean multiply);

    /**
     * Sets the user's experience points to a specific value.
     *
     * @param amount the experience points to set
     * @param checkLevel whether to check and update the user's level based on the new experience
     * @param sendMessage whether to send a message to the user about the experience change
     * @param checkLeaderboard whether to update the leaderboard with the new experience
     */
    void setExp(N amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard);

    /**
     * Sets the user's experience points to a specific value.
     *
     * @param amount the experience points to set, as a string
     * @param checkLevel whether to check and update the user's level based on the new experience
     * @param sendMessage whether to send a message to the user about the experience change
     * @param checkLeaderboard whether to update the leaderboard with the new experience
     */
    void setExp(String amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard);

    /**
     * Sets the user's experience points to a specific value.
     *
     * @param amount the experience points to set
     * @param checkLevel whether to check and update the user's level based on the new experience
     * @param sendMessage whether to send a message to the user about the experience change
     */
    default void setExp(N amount, boolean checkLevel, boolean sendMessage) {
        setExp(amount, checkLevel, sendMessage, true);
    }

    /**
     * Sets the user's experience points to a specific value.
     *
     * @param amount the experience points to set, as a string
     * @param checkLevel whether to check and update the user's level based on the new experience
     * @param sendMessage whether to send a message to the user about the experience change
     */
    default void setExp(String amount, boolean checkLevel, boolean sendMessage) {
        setExp(amount, checkLevel, sendMessage, true);
    }

    /**
     * Removes experience points from the user.
     * @param amount the amount of experience points to remove
     */
    void removeExp(N amount);

    /**
     * Removes experience points from the user.
     * @param amount the amount of experience points to remove, as a string
     */
    void removeExp(String amount);

    /**
     * Checks if the user has a specific permission.
     *
     * @param permission the permission to check
     * @param checkOp whether to consider operator status as having all permissions
     *
     * @return true if the user has the permission, false otherwise
     */
    boolean hasParentPerm(String permission, boolean checkOp);

    /**
     * Gets the experience multiplier for the user.
     * @return the experience multiplier
     */
    double getMultiplier();
}
