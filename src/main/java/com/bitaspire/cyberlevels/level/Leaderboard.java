package com.bitaspire.cyberlevels.level;

import com.bitaspire.cyberlevels.user.LevelUser;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Read-only view of the ranking system maintained by CyberLevels.
 *
 * <p>The leaderboard is built from the currently known user data and is typically refreshed
 * asynchronously. Consumers can inspect the cached ranking, query individual positions, or trigger
 * a new refresh when they need the latest standings.
 *
 * @param <N> numeric type used by the active level engine
 */
public interface Leaderboard<N extends Number> {

    /**
     * Indicates whether a refresh operation is currently in progress.
     *
     * @return {@code true} while the leaderboard is being recomputed
     */
    boolean isUpdating();

    /**
     * Requests a refresh of the cached leaderboard ordering.
     *
     * <p>Implementations are free to perform the actual work asynchronously when ranking data
     * needs to be loaded or sorted outside of the main server thread.
     */
    void update();

    /**
     * Returns the cached top players up to the configured leaderboard size limit.
     *
     * @return ordered list starting at rank {@code 1}
     */
    @NotNull
    List<LevelUser<N>> getTopTenPlayers();

    /**
     * Returns the cached player entry at a specific leaderboard position.
     *
     * @param position one-based leaderboard position
     * @return ranked user at that position, or {@code null} when unavailable
     */
    LevelUser<N> getTopPlayer(int position);

    /**
     * Resolves the cached position of a user currently known to the leaderboard.
     *
     * @param user user whose position should be checked
     * @return one-based position, or {@code -1} when the user is not ranked
     */
    int checkPosition(LevelUser<N> user);

    /**
     * Convenience overload that resolves a position from a live Bukkit player.
     *
     * @param player player whose position should be checked
     * @return one-based position, or {@code -1} when the player is not ranked
     */
    int checkPosition(Player player);
}
