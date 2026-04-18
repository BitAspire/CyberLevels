package com.bitaspire.cyberlevels.user;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates the live user cache used by CyberLevels.
 *
 * <p>The user manager is responsible for loading player data, saving it back to the persistence
 * layer, exposing lookup helpers, and running the periodic auto-save cycle. It is the main bridge
 * between Bukkit players, {@link LevelUser} objects, and the configured {@link Database}.
 *
 * @param <N> numeric type used by the active level engine
 */
public interface UserManager<N extends Number> {

    /**
     * Returns the currently loaded users as a set snapshot.
     *
     * @return loaded users
     */
    @NotNull
    Set<LevelUser<N>> getUsers();

    /**
     * Returns the currently loaded users as a list snapshot.
     *
     * @return loaded users in list form
     */
    @NotNull
    List<LevelUser<N>> getUsersList();

    /**
     * Resolves a loaded user by UUID.
     *
     * @param uuid UUID of the player to resolve
     * @return matching user, or {@code null} when not loaded
     */
    LevelUser<N> getUser(UUID uuid);

    /**
     * Convenience overload that resolves a user from a live player.
     *
     * @param player player whose user object should be resolved
     * @return matching user, or {@code null} when not loaded
     */
    default LevelUser<N> getUser(Player player) {
        return getUser(player.getUniqueId());
    }

    /**
     * Resolves a user by player name.
     *
     * @param name player name to search for
     * @return matching user, or {@code null} when not found
     */
    LevelUser<N> getUser(String name);

    /**
     * Returns the persistence backend currently used by the manager, if any.
     *
     * @return active database implementation, or {@code null} when persistence is disabled
     */
    @Nullable
    Database<N> getDatabase();

    /**
     * Loads an offline player's data into the live cache.
     *
     * @param offline offline player whose data should be loaded
     */
    void loadPlayer(OfflinePlayer offline);

    /**
     * Loads a live player's data into the cache.
     *
     * @param player player whose data should be loaded
     */
    void loadPlayer(Player player);

    /**
     * Saves a live player's data and optionally removes it from memory.
     *
     * @param player player whose data should be saved
     * @param clearData whether the in-memory user should be removed after saving
     */
    void savePlayer(Player player, boolean clearData);

    /**
     * Saves an already resolved user to the persistence layer.
     *
     * @param user user whose data should be saved
     */
    void saveUser(LevelUser<N> user);

    /**
     * Removes a user from both the persistence layer and the live cache when applicable.
     *
     * @param uuid UUID of the player that should be removed
     */
    void removeUser(UUID uuid);

    /**
     * Loads every player currently online on the server.
     */
    void loadOnlinePlayers();

    /**
     * Saves every currently loaded online player.
     *
     * @param clearData whether in-memory entries should be cleared after saving
     */
    void saveOnlinePlayers(boolean clearData);

    /**
     * Starts the repeating auto-save task, if enabled by configuration.
     */
    void startAutoSave();

    /**
     * Cancels the repeating auto-save task, if one is running.
     */
    void cancelAutoSave();
}
