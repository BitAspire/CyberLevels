package net.zerotoil.dev.cyberlevels.objects.leaderboard;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.zerotoil.dev.cyberlevels.CyberLevels;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LeaderboardPlayer implements Comparable {

    final CyberLevels main;
    final String uuid;
    long level;
    double exp;

    public OfflinePlayer getPlayer() {
        if (uuid == null) return null;
        return Bukkit.getOfflinePlayer(UUID.fromString(uuid));
    }

    public int compareTo(Object other) {
        LeaderboardPlayer otherPlayer = (LeaderboardPlayer) other;
        if (this.level > otherPlayer.level) return 1;
        else if (this.level < otherPlayer.level) return -1;
        else return Double.compare(this.exp, otherPlayer.exp);
    }

    public String getUUID() {
        return uuid;
    }

    public long getLevel() {
        OfflinePlayer player = getPlayer();
        if (player != null && player.isOnline()) return main.getLevelCache().playerLevels().get((Player) player).getLevel();
        return level;
    }
    public double getExp() {
        OfflinePlayer player = getPlayer();
        if (player != null && player.isOnline()) return main.getLevelCache().playerLevels().get((Player) player).getExp();
        return exp;
    }

}
