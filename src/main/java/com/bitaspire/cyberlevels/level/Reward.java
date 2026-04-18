package com.bitaspire.cyberlevels.level;

import org.bukkit.entity.Player;

/**
 * Represents a level reward that can be delivered to a player.
 *
 * <p>A reward may contain one or more side effects such as console commands, player messages, and
 * sound playback. Implementations are free to no-op on individual aspects when that part of the
 * reward is not configured.
 */
public interface Reward {

    /**
     * Sends the message portion of the reward to the target player.
     *
     * @param player player who should receive the reward message output
     */
    void sendMessages(Player player);

    /**
     * Executes the command portion of the reward for the target player.
     *
     * @param player player whose context should be used for reward commands
     */
    void executeCommands(Player player);

    /**
     * Plays the configured sound portion of the reward, if any.
     *
     * @param player player who should hear the reward sound
     */
    void playSound(Player player);

    /**
     * Delivers every configured aspect of the reward in the default order used by CyberLevels.
     *
     * @param player player who should receive the reward
     */
    default void giveAll(Player player) {
        executeCommands(player);
        sendMessages(player);
        playSound(player);
    }
}
