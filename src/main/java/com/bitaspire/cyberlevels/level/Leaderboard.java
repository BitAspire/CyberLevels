package com.bitaspire.cyberlevels.level;

import com.bitaspire.cyberlevels.user.LevelUser;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a leaderboard that tracks and manages player rankings based on their levels and experience points.
 *
 * <p> This interface provides methods to check if the leaderboard is updating, to update the leaderboard,
 * to retrieve the top players, and to check a player's position on the leaderboard.
 *
 * @param <N> the numeric type used for experience points and calculations
 */
public interface Leaderboard<N extends Number> {

    /**
     * Checks if the leaderboard is currently updating.
     * @return true if the leaderboard is updating, false otherwise
     */
    boolean isUpdating();

    /**
     * Updates the leaderboard asynchronously.
     */
    void update();

    /**
     * Retrieves a list of the top ten players on the leaderboard.
     * @return a list of the top ten LevelUser objects
     */
    @NotNull
    List<LevelUser<N>> getTopTenPlayers();

    /**
     * Retrieves the player at the specified position on the leaderboard.
     *
     * @param position the position of the player to retrieve (1-based index)
     * @return the LevelUser object at the specified position, or null if not found
     */
    LevelUser<N> getTopPlayer(int position);

    /**
     * Checks the position of a specific user on the leaderboard.
     *
     * @param user the user whose position to check
     * @return the position of the user on the leaderboard (1-based index), or -1 if not found
     */
    int checkPosition(LevelUser<N> user);

    /**
     * Checks the position of a specific player on the leaderboard.
     * @param player the player whose position to check
     * @return the position of the player on the leaderboard (1-based index), or -1 if not found
     */
    int checkPosition(Player player);
}
