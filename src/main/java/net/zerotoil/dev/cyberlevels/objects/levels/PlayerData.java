package net.zerotoil.dev.cyberlevels.objects.levels;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import me.croabeast.beanslib.Beans;
import net.zerotoil.dev.cyberlevels.CyberLevels;
import net.zerotoil.dev.cyberlevels.objects.RewardObject;
import net.zerotoil.dev.cyberlevels.objects.leaderboard.LeaderboardPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerData {

    final CyberLevels main;
    @Getter final Player player;
    @Getter Long level;
    @Setter @Getter Long maxLevel;
    @Getter Double exp;

    long lastTime = 0;
    double lastAmount = 0;

    public PlayerData(CyberLevels main, Player player) {
        this.main = main;
        maxLevel = level = main.getLevelCache().startLevel();
        exp = main.getLevelCache().startExp();
        this.player = player;
    }

    public void addLevel(long amount) {
        long levelCounter = level;
        long newLevel = Math.min(level + Math.max(amount, 0), main.getLevelCache().maxLevel());
        if (nextExpRequirement() == 0) exp = 0.0;

        if (main.getLevelCache().addLevelReward() && levelCounter < newLevel)
            for (long i = levelCounter + 1; i <= newLevel; i++) {
                level++;
                sendLevelReward(i);
            }
        else level = newLevel;

        levelCounter = level - levelCounter;
        if (levelCounter > 0) main.core().sendMessage(player, "gained-levels", new String[]{"{gainedLevels}"}, levelCounter + "");
        checkLeaderboard();
    }

    public void setLevel(long amount, boolean sendMessage) {
        long levelCounter = level;
        if (amount < main.getLevelCache().startLevel()) exp = 0.0;
        else if (amount >= main.getLevelCache().maxLevel()) exp = 0.0;
        level = Math.max(Math.min(amount, main.getLevelCache().maxLevel()), main.getLevelCache().startLevel());
        levelCounter -= level;
        try {
            exp = Math.min(exp, nextExpRequirement());
        } catch (Exception e) {
            // nothing, too lazy to exclude the startup setLevel error lol
        }
        if (!sendMessage) return;
        if (levelCounter > 0) main.core().sendMessage(player, "lost-levels", new String[]{"{lostLevels}"}, Math.abs(levelCounter) + "");
        else if (levelCounter < 0) main.core().sendMessage(player,"gained-levels", new String[]{"{gainedLevels}"}, Math.abs(levelCounter) + "");
        checkLeaderboard();
    }

    public void removeLevel(long amount) {
        if (level - amount < main.getLevelCache().startLevel()) exp = 0.0;
        long levelCounter = level;
        level = Math.max(level - Math.max(amount, 0), main.getLevelCache().startLevel());
        levelCounter -= level;
        if (levelCounter > 0)
            main.core().sendMessage(player, "lost-levels", new String[]{"{lostLevels}"}, levelCounter + "");
        checkLeaderboard();
    }

    public void addExp(double amount, boolean doMultiplier) {
        addExp(amount, 0, true, doMultiplier);
    }

    public void addExp(double amount, double difference, boolean sendMessage, boolean doMultiplier) {
        amount = Math.max(amount, 0);

        // does player have a multiplier permission?
        if (doMultiplier && main.getPlayerUtils().hasParentPerm(player, "CyberLevels.player.multiplier.", false))
            amount *= main.getPlayerUtils().getMultiplier(player);

        final double totalAmount = amount;

        long levelCounter = 0;
        // current exp + exp increase > required exp to next level
        while (exp + amount >= nextExpRequirement()) {
            if (level.equals(main.getLevelCache().maxLevel())) return;
            amount = (amount - nextExpRequirement()) + exp;
            exp = 0.0;
            level++;
            levelCounter++;
            sendLevelReward();
        }
        exp += amount;

        double displayTotal = amount;
        if (main.getLevelCache().isStackComboExp() && System.currentTimeMillis() - lastTime <= 650) displayTotal += lastAmount;

        if (sendMessage && (displayTotal - difference) > 0)
            main.core().sendMessage(player, "gained-exp", new String[]{"{gainedEXP}", "{totalGainedEXP}"},
                    main.getLevelUtils().roundStringDecimal(displayTotal - difference), main.getLevelUtils().roundStringDecimal(totalAmount));

        else if (sendMessage && (displayTotal - difference) < 0)
            main.core().sendMessage(player, "lost-exp", new String[]{"{lostEXP}", "{totalLostEXP}"},
                    main.getLevelUtils().roundStringDecimal(difference - displayTotal), main.getLevelUtils().roundStringDecimal(totalAmount));



        if (sendMessage && levelCounter > 0)
            main.core().sendMessage(player, "gained-levels",
                    new String[]{"{gainedLevels}"}, levelCounter + "");

        lastAmount = displayTotal;
        lastTime = System.currentTimeMillis();
        if (sendMessage) checkLeaderboard();
    }

    public void setExp(double amount, boolean checkLevel, boolean sendMessage) {
        setExp(amount, checkLevel, sendMessage, true);
    }

    public void setExp(double amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard) {
        amount = Math.abs(amount);
        if (checkLevel) {
            double exp = this.exp;
            this.exp = 0.0;
            addExp(amount, exp, sendMessage, false);
        } else exp = amount;
        if (checkLeaderboard) checkLeaderboard();
    }

    public void removeExp(double amount) {
        amount = Math.max(amount, 0);

        final double totalAmount = amount;

        long levelsLost = 0;
        if (amount > exp) {
            if (level.equals(main.getLevelCache().startLevel())) {
                exp = 0.0;
                return;
            }

            amount -= exp;
            level--;
            levelsLost++;
            exp = nextExpRequirement();

            while (amount > exp) {
                if (level.equals(main.getLevelCache().startLevel())) {
                    exp = Math.max(0, exp - amount);
                    amount = 0;
                } else {
                    amount -= nextExpRequirement();
                    level--;
                    levelsLost++;
                    exp = nextExpRequirement();
                }
            }
        }
        //double expTemp = exp;
        exp -= amount;

        double displayTotal = 0 - amount;
        if (main.getLevelCache().isStackComboExp() && System.currentTimeMillis() - lastTime <= 650) displayTotal += lastAmount;

        if (displayTotal < 0) main.core().sendMessage(player, "lost-exp",
                new String[]{"{lostEXP}", "{totalLostEXP}"}, main.getLevelUtils().roundStringDecimal(Math.abs(displayTotal)), main.getLevelUtils().roundStringDecimal(totalAmount));

        else if (displayTotal > 0) main.core().sendMessage(player, "gained-exp",
                new String[]{"{gainedEXP}", "{totalGainedEXP}"}, main.getLevelUtils().roundStringDecimal(displayTotal), main.getLevelUtils().roundStringDecimal(totalAmount));

        if (levelsLost > 0) main.core().sendMessage(player, "lost-levels",
                new String[]{"{lostLevels}"}, levelsLost + "");

        lastAmount = displayTotal;
        lastTime = System.currentTimeMillis();

        // makes sure the level doesn't go down below the start level
        level = Math.max(main.getLevelCache().startLevel(), level);
        exp = Math.max(0, exp);
        checkLeaderboard();
    }

    @Override
    public String toString() {
        return "level: " + level + ", exp: " + exp + ", progress: " +
                Beans.colorize(main.getLevelUtils().progressBar(exp, nextExpRequirement())) +
                " [" + (int) (100 * (exp / nextExpRequirement())) + "%]";
    }

    private void sendLevelReward(long level) {
        if (main.getLevelCache().isPreventDuplicateRewards() && level <= maxLevel) return;
        for (RewardObject rewardObject : main.getLevelCache().levelData().get(level).getRewards()) rewardObject.giveReward(player);
        maxLevel = level;
    }

    private void sendLevelReward() {
        sendLevelReward(level);
    }

    public double nextExpRequirement() {
        if (main.getLevelCache().levelData().get(level + 1) == null) return 0.0;
        return main.getLevelCache().levelData().get(level + 1).getRequiredExp(player);
    }

    private void checkLeaderboard() {
        final LevelCache levelCache = main.getLevelCache();
        if (!levelCache.isLeaderboardInstantUpdate()) return;
        if (levelCache.getLeaderboard().isUpdating()) return;

        Bukkit.getScheduler().runTaskAsynchronously(main, () -> {
            // checks if player is promoted
            int startFrom = main.getLevelCache().getLeaderboard().checkFrom(player);
            List<LeaderboardPlayer> topPlayers = main.getLevelCache().getLeaderboard().getTopTenPlayers();
            boolean movedUp = false;
            for (int i = startFrom; i >= 1; i--) {
                LeaderboardPlayer lbp = main.getLevelCache().getLeaderboard().getTopPlayer(i);
                if (lbp.getUUID() == null || lbp.getUUID().equals(player.getUniqueId().toString())) continue;
                if (level < lbp.getLevel()) break;
                if (level == lbp.getLevel() && exp < lbp.getExp()) break;
                LeaderboardPlayer cp = new LeaderboardPlayer(main, player.getUniqueId().toString(), level, exp);
                if (topPlayers.size() > i)
                    topPlayers.set(i, topPlayers.get(i - 1)); // puts the above player down
                topPlayers.set(i - 1, cp);
                movedUp = true;
            }
            if (movedUp) return;

            // checks if player is demoted
            for (int i = startFrom; i <= 10; i++) {
                LeaderboardPlayer lbp = main.getLevelCache().getLeaderboard().getTopPlayer(i);
                if (lbp.getUUID() == null) break;
                if (lbp.getUUID().equals(player.getUniqueId().toString())) continue;
                if (level > lbp.getLevel()) break;
                if (level == lbp.getLevel() && exp > lbp.getExp()) break;
                LeaderboardPlayer cp = new LeaderboardPlayer(main, player.getUniqueId().toString(), level, exp);
                topPlayers.set(i - 2, topPlayers.get(i - 1));
                if (topPlayers.size() > i)
                    topPlayers.set(i - 1, cp);
                if (topPlayers.size() == i) main.getLevelCache().getLeaderboard().updateLeaderboard();
            }
        });
    }

}
