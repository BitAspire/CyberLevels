package com.bitaspire.cyberlevels.user;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Persistence abstraction used by CyberLevels to store user progress.
 *
 * <p>Implementations may be backed by flat files, SQLite, MySQL, PostgreSQL, or any other storage
 * mechanism supported by the plugin. The interface intentionally focuses on the lifecycle and CRUD
 * operations required by the user manager.
 *
 * @param <N> numeric type used by the active level engine
 */
public interface Database<N extends Number> {

    /**
     * Indicates whether the database layer is currently connected and ready for use.
     *
     * @return {@code true} when the persistence backend is available
     */
    boolean isConnected();

    /**
     * Establishes the underlying connection or prepares the backing storage.
     */
    void connect();

    /**
     * Closes the underlying connection and releases any held resources.
     */
    void disconnect();

    /**
     * Checks whether the supplied user already exists in the backing store.
     *
     * @param user user whose persistence state should be checked
     * @return {@code true} when a backing record already exists
     */
    boolean isUserLoaded(LevelUser<N> user);

    /**
     * Inserts a user into the backing store.
     *
     * @param user user to insert
     * @param defValues whether default level/EXP values should be applied during insertion
     */
    void addUser(LevelUser<N> user, boolean defValues);

    /**
     * Inserts a user and applies the default values expected for first-time entries.
     *
     * @param user user to insert
     */
    default void addUser(LevelUser<N> user) {
        addUser(user, true);
    }

    /**
     * Persists the current state of a user.
     *
     * @param user user whose data should be written
     */
    void updateUser(LevelUser<N> user);

    /**
     * Persists the current state of a user synchronously on the calling thread.
     *
     * <p>Implementations that do not need a dedicated synchronous path may safely delegate to
     * {@link #updateUser(LevelUser)}.
     *
     * @param user user whose data should be written
     */
    default void updateUserSync(LevelUser<N> user) {
        updateUser(user);
    }

    /**
     * Removes a user record from the backing store.
     *
     * @param uuid UUID of the player that should be deleted
     */
    void removeUser(UUID uuid);

    /**
     * Loads or resolves a user by UUID.
     *
     * @param uuid UUID to resolve
     * @return corresponding user instance
     */
    LevelUser<N> getUser(UUID uuid);

    /**
     * Convenience overload that resolves a user from a live player object.
     *
     * @param player player whose data should be resolved
     * @return corresponding user instance
     */
    LevelUser<N> getUser(Player player);

    /**
     * Returns the set of UUIDs known to the persistence backend.
     *
     * @return UUID snapshot of stored users
     */
    @NotNull
    Set<UUID> getUuids();
}
