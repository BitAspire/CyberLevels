package com.bitaspire.cyberlevels.user;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Mutable view of one player's progression data.
 *
 * <p>A {@code LevelUser} wraps identity, online state, current level, current EXP, progression
 * helpers, and mutation operations such as adding EXP or changing levels. Implementations are
 * expected to keep the in-memory state synchronized with the active level system and persistence
 * backend.
 *
 * @param <N> numeric type used by the active level engine
 */
public interface LevelUser<N extends Number> extends Comparable<LevelUser<N>> {

    /**
     * Returns the unique identifier of this user.
     *
     * @return player UUID
     */
    @NotNull
    UUID getUuid();

    /**
     * Returns the Bukkit offline player handle associated with this user.
     *
     * @return offline player view
     */
    OfflinePlayer getOffline();

    /**
     * Returns the live Bukkit player handle.
     *
     * <p>Implementations may throw if the user is currently offline, so callers that are not sure
     * about connection state should prefer {@link #isOnline()} or {@link #getOffline()} first.
     *
     * @return live player handle
     */
    @NotNull
    Player getPlayer();

    /**
     * Returns the most relevant player name for this user.
     *
     * @return player name as stored or resolved by the implementation
     */
    @NotNull
    String getName();

    /**
     * Indicates whether the player is currently online.
     *
     * @return {@code true} when a live Bukkit player is available
     */
    boolean isOnline();

    /**
     * Returns the user's current level.
     *
     * @return current level
     */
    long getLevel();

    /**
     * Adds one or more levels to the user.
     *
     * @param amount number of levels to add
     */
    void addLevel(long amount);

    /**
     * Sets the user's level to an explicit value.
     *
     * @param amount target level
     * @param sendMessage whether the user should receive the standard level-change feedback
     */
    void setLevel(long amount, boolean sendMessage);

    /**
     * Removes one or more levels from the user.
     *
     * @param amount number of levels to remove
     */
    void removeLevel(long amount);

    /**
     * Returns the user's current EXP value.
     *
     * @return current EXP
     */
    @NotNull
    N getExp();

    /**
     * Returns the EXP required for the user to reach the next level.
     *
     * @return required EXP for the current level
     */
    @NotNull
    N getRequiredExp();

    /**
     * Returns how much EXP the user still needs before leveling up.
     *
     * @return remaining EXP to the next level
     */
    @NotNull
    N getRemainingExp();

    /**
     * Returns the formatted completion percentage toward the next level.
     *
     * @return percentage string ready for display
     */
    @NotNull
    String getPercent();

    /**
     * Returns the formatted progress bar for the user's current EXP state.
     *
     * @return progress bar string ready for display
     */
    @NotNull
    String getProgressBar();

    /**
     * Adds EXP to the user.
     *
     * @param amount EXP amount to add
     * @param multiply whether the player's multiplier should be applied first
     */
    void addExp(N amount, boolean multiply);

    /**
     * Adds EXP to the user from a primitive {@code double} value.
     *
     * <p>This overload exists for hot event paths where a primitive value is already available.
     *
     * @param amount EXP amount to add
     * @param multiply whether the player's multiplier should be applied first
     */
    default void addExp(double amount, boolean multiply) {
        addExp(String.valueOf(amount), multiply);
    }

    /**
     * Adds EXP to the user from a string representation.
     *
     * @param amount EXP amount to add as text
     * @param multiply whether the player's multiplier should be applied first
     */
    void addExp(String amount, boolean multiply);

    /**
     * Sets the user's EXP to an explicit value.
     *
     * @param amount target EXP value
     * @param checkLevel whether level-up or level-down logic should run afterward
     * @param sendMessage whether the user should receive the standard EXP-change feedback
     * @param checkLeaderboard whether the leaderboard should be refreshed or marked dirty
     */
    void setExp(N amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard);

    /**
     * Sets the user's EXP from a string representation.
     *
     * @param amount target EXP value as text
     * @param checkLevel whether level-up or level-down logic should run afterward
     * @param sendMessage whether the user should receive the standard EXP-change feedback
     * @param checkLeaderboard whether the leaderboard should be refreshed or marked dirty
     */
    void setExp(String amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard);

    /**
     * Sets the user's EXP from a primitive {@code double}.
     *
     * @param amount target EXP value
     * @param checkLevel whether level-up or level-down logic should run afterward
     * @param sendMessage whether the user should receive the standard EXP-change feedback
     * @param checkLeaderboard whether the leaderboard should be refreshed or marked dirty
     */
    default void setExp(double amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard) {
        setExp(String.valueOf(amount), checkLevel, sendMessage, checkLeaderboard);
    }

    /**
     * Sets the user's EXP and keeps leaderboard updates enabled by default.
     *
     * @param amount target EXP value
     * @param checkLevel whether level-up or level-down logic should run afterward
     * @param sendMessage whether the user should receive the standard EXP-change feedback
     */
    default void setExp(N amount, boolean checkLevel, boolean sendMessage) {
        setExp(amount, checkLevel, sendMessage, true);
    }

    /**
     * Sets the user's EXP from a string value and keeps leaderboard updates enabled by default.
     *
     * @param amount target EXP value as text
     * @param checkLevel whether level-up or level-down logic should run afterward
     * @param sendMessage whether the user should receive the standard EXP-change feedback
     */
    default void setExp(String amount, boolean checkLevel, boolean sendMessage) {
        setExp(amount, checkLevel, sendMessage, true);
    }

    /**
     * Removes EXP from the user.
     *
     * @param amount EXP amount to remove
     */
    void removeExp(N amount);

    /**
     * Removes EXP from the user from a primitive {@code double}.
     *
     * <p>This overload exists for hot event paths where a primitive value is already available.
     *
     * @param amount EXP amount to remove
     */
    default void removeExp(double amount) {
        removeExp(String.valueOf(amount));
    }

    /**
     * Removes EXP from the user from a string representation.
     *
     * @param amount EXP amount to remove as text
     */
    void removeExp(String amount);

    /**
     * Checks whether the user has a permission, optionally treating operator status as a wildcard.
     *
     * @param permission permission node to check
     * @param checkOp whether operator status should automatically pass the check
     * @return {@code true} when the user satisfies the permission requirement
     */
    boolean hasParentPerm(String permission, boolean checkOp);

    /**
     * Returns the EXP multiplier currently applicable to the user.
     *
     * @return effective multiplier
     */
    double getMultiplier();
}
