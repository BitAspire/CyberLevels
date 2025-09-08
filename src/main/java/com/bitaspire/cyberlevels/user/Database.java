package com.bitaspire.cyberlevels.user;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Represents a database interface for managing user data in a leveling system.
 *
 * <p> This interface provides methods to connect, disconnect, and manage user data
 * within the database.
 *
 * @param <N> the numeric type used for levels and experience points
 */
public interface Database<N extends Number> {

    /**
     * Checks if the database is currently connected.
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Establishes a connection to the database.
     */
    void connect();

    /**
     * Disconnects from the database.
     */
    void disconnect();

    /**
     * Checks if a user is loaded in the database.
     *
     * @param user the LevelUser to check
     * @return true if the user is loaded, false otherwise
     */
    boolean isUserLoaded(LevelUser<N> user);

    /**
     * Adds a user to the database.
     *
     * @param user the LevelUser to add
     * @param defValues if true, default values will be set for the user
     */
    void addUser(LevelUser<N> user, boolean defValues);

    /**
     * Adds a user to the database with default values.
     * @param user the LevelUser to add
     */
    default void addUser(LevelUser<N> user) {
        addUser(user, true);
    }

    /**
     * Updates the user's data in the database.
     * @param user the LevelUser to update
     */
    void updateUser(LevelUser<N> user);

    void removeUser(UUID uuid);

    /**
     * Retrieves a LevelUser instance for the given uuid.
     *
     * @param uuid the UUID to retrieve the LevelUser for
     * @return the LevelUser instance
     */
    LevelUser<N> getUser(UUID uuid);

    /**
     * Retrieves a LevelUser instance for the given player.
     *
     * @param player the Player to retrieve the LevelUser for
     * @return the LevelUser instance
     */
    LevelUser<N> getUser(Player player);

    @NotNull
    Set<UUID> getUuids();
}
