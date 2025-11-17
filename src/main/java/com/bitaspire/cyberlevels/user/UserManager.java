package com.bitaspire.cyberlevels.user;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a manager for handling user data and interactions within a leveling system.
 *
 * <p> This interface provides methods to handle user-related operations, including interaction
 * with a database if available.
 *
 * @param <N> the numeric type used for experience points and calculations
 */
public interface UserManager<N extends Number> {

    /**
     * Gets a set of all users managed by this UserManager.
     * @return a set of LevelUser objects
     */
    @NotNull
    Set<LevelUser<N>> getUsers();

    @NotNull
    List<LevelUser<N>> getUsersList();

    /**
     * Retrieves a user by their UUID.
     *
     * @param uuid the UUID of the player
     * @return the LevelUser object associated with the UUID, or null if not found
     */
    LevelUser<N> getUser(UUID uuid);

    /**
     * Retrieves a user by their Player object.
     *
     * @param player the Player object
     * @return the LevelUser object associated with the Player, or null if not found
     */
    default LevelUser<N> getUser(Player player) {
        return getUser(player.getUniqueId());
    }

    LevelUser<N> getUser(String name);

    /**
     * Gets the database associated with this UserManager, if any.
     * @return the Database object, or null if no database is used
     */
    @Nullable
    Database<N> getDatabase();

    /**
     * Loads the player data into the system.
     * @param offline the UUID object to load
     */
    void loadPlayer(OfflinePlayer offline);

    /**
     * Loads the player data into the system.
     * @param player the Player object to load
     */
    void loadPlayer(Player player);

    /**
     * Saves the player data to the system.
     *
     * @param player the Player object to save
     * @param clearData if true, clears the player's data from memory after saving
     */
    void savePlayer(Player player, boolean clearData);

    /**
     * Saves the user data to the system.
     * @param user the LevelUser object to save
     */
    void saveUser(LevelUser<N> user);

    void removeUser(UUID uuid);

    /**
     * Loads all currently online players into the system.
     */
    void loadOnlinePlayers();

    /**
     * Saves all currently online players' data to the system.
     * @param clearData if true, clears all players' data from memory after saving
     */
    void saveOnlinePlayers(boolean clearData);

    /**
     * Starts the auto-save task to periodically save user data.
     */
    void startAutoSave();

    /**
     * Cancels the auto-save task.
     */
    void cancelAutoSave();
}
