package com.bitaspire.cyberlevels.level;

import org.bukkit.entity.Player;

/**
 * Represents a reward that can be given to a player, including messages, commands, items, and sounds.
 */
public interface Reward {

    /**
     * Sends reward messages to the specified player.
     * @param player the player to send messages to
     */
    void sendMessages(Player player);

    /**
     * Executes reward commands for the specified player.
     * @param player the player to execute commands for
     */
    void executeCommands(Player player);

    /**
     * Plays a reward sound for the specified player.
     * @param player the player to play the sound for
     */
    void playSound(Player player);

    /**
     * Gives all aspects of the reward (messages, commands, sound) to the specified player.
     * @param player the player to give the reward to
     */
    default void giveAll(Player player) {
        executeCommands(player);
        sendMessages(player);
        playSound(player);
    }
}
