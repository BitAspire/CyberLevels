package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.user.UserManager;
import com.bitaspire.libs.formula.expression.ExpressionBuilder;
import com.bitaspire.cyberlevels.cache.Cache;
import com.bitaspire.cyberlevels.cache.Lang;
import com.bitaspire.cyberlevels.level.*;
import com.bitaspire.cyberlevels.user.LevelUser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.croabeast.beanslib.Beans;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
abstract class BaseSystem<N extends Number> implements LevelSystem<N> {

    final CyberLevels main;
    final Cache cache;

    private final long startLevel, maxLevel;
    private final int startExp;

    private final Formula<N> formula;

    private final Map<Long, Formula<N>> formulas = new ConcurrentHashMap<>();
    private final Map<Long, List<Reward>> rewardMap = new ConcurrentHashMap<>();

    DecimalFormatter<N> formatter = null;
    UserManager<N> userManager = null;

    BaseLeaderboard<N> leaderboard = null;

    BaseSystem(CyberLevels main) {
        this.main = main;

        long l = System.currentTimeMillis();

        cache = main.cache();

        startExp = cache.levels().getStartExp();
        startLevel = cache.levels().getStartLevel();
        maxLevel = cache.levels().getMaxLevel();

        formula = createFormula(cache.levels().getFormula());
        cache.levels().getCustomFormulas().forEach((k, v) -> formulas.put(k, createFormula(v)));

        rewardMap.putAll(cache.rewards().getRewards());
        if (cache.config().isRoundingEnabled()) formatter = new DecimalFormatter<>(this);
    }

    abstract Formula<N> createFormula(String formula);

    @Override
    public Formula<N> getCustomFormula(long level) {
        return formulas.get(level);
    }

    @NotNull
    public String roundString(N amount) {
        if (amount == null) {
            return "0";
        }

        return formatter != null ? formatter.format(amount) : getOperator().toString(amount);
    }

    @NotNull
    public N round(N amount) {
        if (amount == null) {
            return getOperator().zero();
        }

        if (formatter == null) {
            return amount;
        }

        return getOperator().valueOf(formatter.format(amount));
    }

    @Override
    public double roundDouble(N amount) {
        if (amount == null) {
            return 0D;
        }

        if (formatter == null) {
            return amount.doubleValue();
        }

        return Double.parseDouble(formatter.format(amount));
    }

    @NotNull
    public String formatNumber(Number value) {
        if (value == null) return "0";
        if (formatter == null) return value.toString();

        N amount = getOperator().valueOf(String.valueOf(value));
        return roundString(amount);
    }

    @NotNull
    public List<Reward> getRewards(long level) {
        return rewardMap.getOrDefault(level, new ArrayList<>());
    }

    @NotNull
    public N getRequiredExp(long level, UUID uuid) {
        return formulas.getOrDefault(level, formula).evaluate(uuid);
    }

    @NotNull
    public String replacePlaceholders(String string, UUID uuid, boolean safeForFormula) {
        LevelUser<N> data = userManager.getUser(uuid);

        String[] keys = {"{level}", "{playerEXP}", "{nextLevel}",
                "{maxLevel}", "{minLevel}", "{minEXP}"};
        String[] values = {
                String.valueOf(data.getLevel()),
                roundString(data.getExp()),
                String.valueOf(data.getLevel() + 1),
                String.valueOf(maxLevel),
                String.valueOf(startLevel),
                String.valueOf(startExp)
        };
        string = StringUtils.replaceEach(string, keys, values);

        String[] k = {"{player}", "{playerDisplayName}", "{playerUUID}"};
        String[] v = {
                data.getName(), data.isOnline() ? data.getPlayer().getDisplayName() : data.getName(),
                data.getUuid().toString()
        };
        string = StringUtils.replaceEach(string, k, v);

        if (!safeForFormula) {
            k = new String[] {"{requiredEXP}", "{percent}", "{progressBar}"};
            v = new String[] {
                    roundString(data.getRequiredExp()),
                    data.getPercent(), data.getProgressBar()
            };
            string = StringUtils.replaceEach(string, k, v);
        }

        return Beans.formatPlaceholders(data.isOnline() ? data.getPlayer() : null, string);
    }

    @NotNull
    public String getProgressBar(N exp, N requiredExp) {
        Lang lang = cache.lang();

        String startBar = lang.getProgressCompleteColor();
        String middleBar = lang.getProgressIncompleteColor();
        String bar = lang.getProgressBar();
        String endBar = lang.getProgressEndColor();

        String def = startBar + middleBar + bar + endBar;
        if (getOperator().compare(requiredExp, getOperator().zero()) == 0)
            return def;

        int length = bar.length();

        N scaled = getOperator().multiply(exp, getOperator().fromDouble(length));
        N divided = getOperator().divide(scaled, requiredExp, 0, RoundingMode.DOWN);

        int completion = Math.min(divided.intValue(), length);
        if (completion <= 0) return def;

        return startBar + bar.substring(0, completion) +
                middleBar + bar.substring(completion) + endBar;
    }

    @NotNull
    public String getPercent(N exp, N requiredExp) {
        if (getOperator().compare(requiredExp, getOperator().zero()) == 0) return "0";
        if (getOperator().compare(exp, requiredExp) >= 0) return "100";

        N scaled = getOperator().multiply(exp, getOperator().fromDouble(100));
        N divided = getOperator().divide(scaled, requiredExp, 0, RoundingMode.DOWN);

        return getOperator().toString(divided);
    }

    @Setter
    Function<UserManager<N>, BaseLeaderboard<N>> leaderboardFunction;

    void setUserManager(UserManager<N> manager) {
        leaderboard = leaderboardFunction.apply((userManager = manager));
    }

    @NotNull
    public Map<String, ExpSource> getExpSources() {
        return cache.earnExp().getExpSources();
    }

    @NotNull
    public Map<String, AntiAbuse> getAntiAbuses() {
        return cache.antiAbuse().getAntiAbuses();
    }

    @NotNull
    LevelUser<N> createUser(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ?
                new OfflineUser<>(this, Bukkit.getOfflinePlayer(uuid)) :
                new OnlineUser<>(this, player);
    }

    @NotNull
    LevelUser<N> createOffline(UUID uuid) {
        return new OfflineUser<>(this, Bukkit.getOfflinePlayer(uuid));
    }

    @NotNull
    LevelUser<N> createUser(LevelUser<?> user) {
        LevelUser<N> newUser = createUser(user.getUuid());

        newUser.setLevel(user.getLevel(), false);
        newUser.setExp(user.getExp() + "", true, false, false);

        return newUser;
    }

    static class DecimalFormatter<T extends Number> {

        final DecimalFormat decimalFormat;

        DecimalFormatter(BaseSystem<T> system) {
            int decimals = system.cache.config().getRoundingDigits();

            StringBuilder pattern = new StringBuilder("#");
            if (decimals > 0) {
                pattern.append(".");
                for (int i = 0; i < decimals; i++)
                    pattern.append("#");
            }

            decimalFormat = new DecimalFormat(pattern.toString());
            decimalFormat.setRoundingMode(RoundingMode.CEILING);
            decimalFormat.setMinimumFractionDigits(decimals);
        }

        @NotNull
        String format(T value) {
            return decimalFormat.format(value).replace(",", ".");
        }
    }

    @RequiredArgsConstructor
    abstract class BaseFormula<T extends Number> implements Formula<T> {

        private final Operator<T> operator;
        @Getter
        private final String asString;

        abstract ExpressionBuilder<T> builder();

        @NotNull
        public T evaluate(UUID uuid) {
            String parsed = replacePlaceholders(asString, uuid, true);
            if (StringUtils.isBlank(parsed))
                return operator.fromDouble(0.0);

            try {
                return builder().build(parsed).evaluate();
            } catch (Throwable t) {
                return operator.fromDouble(0.0);
            }
        }
    }

    @Getter
    abstract class BaseLeaderboard<T extends Number> implements Leaderboard<T> {

        private final UserManager<T> userManager;

        private volatile boolean updating = false;
        protected final List<Entry<T>> topTenPlayers = new CopyOnWriteArrayList<>();

        BaseLeaderboard(UserManager<T> manager) {
            this.userManager = manager;
        }

        @NotNull
        public List<LevelUser<T>> getTopTenPlayers() {
            return topTenPlayers.stream().map(Entry::getUser).collect(Collectors.toList());
        }

        @Override
        public void update() {
            List<LevelUser<T>> users = userManager.getUsersList();
            updating = true;

            Bukkit.getScheduler().runTaskAsynchronously(main, () -> {
                List<Entry<T>> list = new ArrayList<>();
                for (LevelUser<T> user : users) list.add(toEntry(user));

                list.sort(Comparator.naturalOrder());
                List<Entry<T>> top10 = list.subList(0, Math.min(10, list.size()));

                Bukkit.getScheduler().runTask(main, () -> {
                    topTenPlayers.clear();
                    topTenPlayers.addAll(top10);
                    updating = false;
                });
            });
        }

        @Override
        public LevelUser<T> getTopPlayer(int position) {
            return updating || position < 1 || position > 10 ? null : userManager.getUser(topTenPlayers.get(position - 1).getUuid());
        }

        @Override
        public int checkPosition(Player player) {
            UUID uuid = player.getUniqueId();

            for (int i = 0; i < topTenPlayers.size(); i++)
                if (uuid.equals(topTenPlayers.get(i).getUuid()))
                    return i + 1;

            return -1;
        }

        @Override
        public int checkPosition(LevelUser<T> user) {
            return checkPosition(user.getPlayer());
        }

        abstract Entry<T> toEntry(LevelUser<T> user);

        @Getter
        abstract class Entry<X extends Number> implements Comparable<Entry<X>> {

            private final UUID uuid;
            private final String name;
            private final long level;
            private final X exp;
            private final LevelUser<X> user;

            Entry(UUID uuid, String name, long level, X exp, LevelUser<X> user) {
                this.uuid = uuid;
                this.name = name;
                this.level = level;
                this.exp = exp;
                this.user = user;
            }
        }
    }

    void updateLeaderboard() {
        if (!main.isEnabled() || leaderboard == null) return;

        if (cache.config().leaderboardInstantUpdate() && !leaderboard.isUpdating())
            leaderboard.update();
    }

    abstract class BaseUser<T extends Number> implements LevelUser<T> {

        private final BaseSystem<T> system;
        private final Operator<T> operator;

        @Getter
        private final UUID uuid;

        @Getter
        long level;

        T exp, lastAmount;
        long lastTime = 0L;

        @Getter
        private long highestRewardedLevel;

        public void setHighestRewardedLevel(long value) {
            this.highestRewardedLevel = Math.max(0L, value);
        }

        BaseUser(BaseSystem<T> system, UUID uuid) {
            this.uuid = uuid;
            exp = (this.operator = (this.system = system).getOperator()).fromDouble(getStartExp());
            level = system.getStartLevel();
            lastAmount = operator.zero();
            highestRewardedLevel = Math.max(0L, level - 1);
        }

        void sendLevelReward(long level) {
            // Only give rewards if player is online
            if (!isOnline()) {
                // Mark as rewarded but don't give items/commands to offline player
                if (cache.config().preventDuplicateRewards() && level > getHighestRewardedLevel()) {
                    setHighestRewardedLevel(level);
                }
                return;
            }

            try {
                if (!cache.config().preventDuplicateRewards()) {
                    getRewards(level).forEach(r -> r.giveAll(getPlayer()));
                    return;
                }

                if (level > getHighestRewardedLevel()) {
                    getRewards(level).forEach(r -> r.giveAll(getPlayer()));
                    setHighestRewardedLevel(level);
                }
            } catch (IllegalStateException e) {
                // Player not online, skip rewards
            }
        }

        void updateLevel(long newLevel, boolean sendMessage, boolean giveRewards) {
            long oldLevel = level;

            if (operator.compare(exp, operator.zero()) < 0) {
                exp = operator.zero();
            }

            if (giveRewards && cache.config().addLevelRewards() && oldLevel < newLevel) {
                for (long i = oldLevel + 1; i <= newLevel; i++) {
                    level++;
                    sendLevelReward(i);
                }
            } else {
                level = newLevel;
            }

            if (operator.compare(exp, operator.zero()) < 0) exp = operator.zero();

            // Only send messages if player is online
            if (sendMessage && isOnline()) {
                try {
                    long diff = level - oldLevel;
                    if (diff > 0) {
                        cache.lang().sendMessage(getPlayer(), Lang::getGainedLevels, "gainedLevels", diff);
                    } else if (diff < 0) {
                        cache.lang().sendMessage(getPlayer(), Lang::getLostLevels, "lostLevels", Math.abs(diff));
                    }
                } catch (IllegalStateException e) {
                    // Player not online, skip messages
                }
            }

            system.updateLeaderboard();
        }

        public void addLevel(long amount) {
            long target = Math.min(level + Math.max(amount, 0), getMaxLevel());
            updateLevel(target, true, true);
        }

        public void setLevel(long amount, boolean sendMessage) {
            long min = getStartLevel(); long max = getMaxLevel();
            long target = Math.max(Math.min(amount, max), min);

            if (amount < min || amount >= max) exp = operator.zero();
            updateLevel(target, sendMessage, false);
        }

        public void removeLevel(long amount) {
            long target = Math.max(level - Math.max(amount, 0), getStartLevel());
            updateLevel(target, true, false);
        }

        private void changeExp(T amount, T difference, boolean sendMessage, boolean doMultiplier, boolean checkLeaderboard) {
            if (operator.compare(amount, operator.zero()) == 0) return;

            if (operator.compare(amount, operator.zero()) > 0 && level >= getMaxLevel())
                return;

            // Only apply multiplier if player is online (requires permission check)
            if (doMultiplier && operator.compare(amount, operator.zero()) > 0 && isOnline()) {
                try {
                    if (hasParentPerm("CyberLevels.player.multiplier.", false)) {
                        amount = operator.multiply(amount, operator.fromDouble(getMultiplier()));
                    }
                } catch (IllegalStateException e) {
                    // Player not online, skip multiplier
                }
            }

            final T totalAmount = amount;
            long levelsChanged = 0;

            if (operator.compare(amount, operator.zero()) > 0) {
                while (operator.compare(operator.add(exp, amount), rawRequiredExp()) >= 0) {
                    if (level == getMaxLevel()) {
                        exp = operator.zero();
                        return;
                    }

                    amount = operator.add(operator.subtract(amount, rawRequiredExp()), exp);
                    exp = operator.zero();
                    level++;
                    levelsChanged++;
                    sendLevelReward(level);
                }

                exp = operator.add(exp, amount);
            }
            else {
                amount = operator.abs(amount);
                if (operator.compare(amount, exp) > 0) {
                    while (operator.compare(amount, exp) > 0 && level > getStartLevel()) {
                        amount = operator.subtract(amount, exp);
                        level--;
                        levelsChanged--;
                        exp = rawRequiredExp();
                    }
                    exp = operator.subtract(exp, amount);
                    if (operator.compare(exp, operator.zero()) < 0) exp = operator.zero();
                }
                else {
                    exp = operator.subtract(exp, amount);
                }
            }

            T displayTotal = (cache.config().stackComboExp() && System.currentTimeMillis() - lastTime <= 650)
                    ? operator.add(amount, lastAmount) : amount;

            // Only send messages if player is online
            if (sendMessage && isOnline()) {
                try {
                    T diff = operator.subtract(Objects.equals(displayTotal, operator.zero()) ? operator.zero() : displayTotal, difference);

                    if (operator.compare(totalAmount, operator.zero()) > 0) {
                        cache.lang().sendMessage(
                                getPlayer(), Lang::getGainedExp, new String[] {"gainedEXP", "totalGainedEXP"},
                                system.roundString(diff), system.roundString(totalAmount)
                        );
                    } else if (operator.compare(totalAmount, operator.zero()) < 0) {
                        cache.lang().sendMessage(
                                getPlayer(), Lang::getLostExp, new String[] {"lostEXP", "totalLostEXP"},
                                system.roundString(operator.abs(diff)), system.roundString(operator.abs(totalAmount))
                        );
                    }

                    if (levelsChanged > 0) {
                        cache.lang().sendMessage(getPlayer(), Lang::getGainedLevels, "gainedLevels", levelsChanged);
                    } else if (levelsChanged < 0) {
                        cache.lang().sendMessage(getPlayer(), Lang::getLostLevels, "lostLevels", Math.abs(levelsChanged));
                    }
                } catch (IllegalStateException e) {
                    // Player not online, skip messages
                }
            }

            lastAmount = displayTotal;
            lastTime = System.currentTimeMillis();

            level = Math.max(getStartLevel(), Math.min(level, getMaxLevel()));
            if (operator.compare(exp, operator.zero()) < 0) exp = operator.zero();

            if (checkLeaderboard) system.updateLeaderboard();
        }

        public void addExp(T amount, boolean doMultiplier) {
            changeExp(amount, operator.zero(), true, doMultiplier, true);
        }

        @Override
        public void addExp(String amount, boolean multiply) {
            addExp(operator.valueOf(amount), multiply);
        }

        public void setExp(T amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard) {
            amount = operator.abs(amount);

            if (checkLevel) {
                T oldExp = this.exp;
                exp = operator.zero();
                changeExp(amount, oldExp, sendMessage, false, checkLeaderboard);
            }
            else this.exp = amount;

            if (checkLeaderboard) system.updateLeaderboard();
        }

        @Override
        public void setExp(String amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard) {
            setExp(operator.valueOf(amount), checkLevel, sendMessage, checkLeaderboard);
        }

        public void removeExp(T amount) {
            T positive = operator.max(amount, operator.zero());
            T negative = operator.negate(positive);
            changeExp(negative, operator.zero(), true, false, true);
        }

        @Override
        public void removeExp(String amount) {
            removeExp(operator.valueOf(amount));
        }

        @NotNull
        public T getExp() {
            return system.round(exp);
        }

        private T rawRequiredExp() {
            return system.getRequiredExp(level, uuid);
        }

        @NotNull
        public T getRequiredExp() {
            return system.round(rawRequiredExp());
        }

        @NotNull
        public T getRemainingExp() {
            return system.round(operator.subtract(rawRequiredExp(), exp));
        }

        @NotNull
        public String getPercent() {
            return system.getPercent(exp, getRequiredExp());
        }

        @NotNull
        public String getProgressBar() {
            return system.getProgressBar(exp, getRequiredExp());
        }

        @Override
        public boolean hasParentPerm(String permission, boolean checkOp) {
            if (checkOp && getPlayer().isOp()) return true;

            for (PermissionAttachmentInfo node : getPlayer().getEffectivePermissions()) {
                if (!node.getValue()) continue;
                if (node.getPermission().toLowerCase().startsWith(permission.toLowerCase()))
                    return true;
            }

            return false;
        }

        @Override
        public double getMultiplier() {
            double multiplier = 0;

            for (PermissionAttachmentInfo perm : getPlayer().getEffectivePermissions()) {
                if (!perm.getValue()) continue;

                String s = perm.getPermission().toLowerCase(Locale.ENGLISH);
                if (!s.startsWith("cyberlevels.player.multiplier."))
                    continue;

                try {
                    double current = Double.parseDouble(s.substring(30));
                    if (current > multiplier) multiplier = current;
                } catch (Exception ignored) {}
            }

            return multiplier == 0 ? 1 : multiplier;
        }

        @Override
        public int compareTo(@NotNull LevelUser<T> o) {
            return system.getLeaderboard().toEntry(this).compareTo(system.getLeaderboard().toEntry(o));
        }

        @Override
        public String toString() {
            return "LevelUser{" +
                    "player=" + getName() +
                    ", uuid=" + uuid +
                    ", level=" + level +
                    ", exp=" + getExp() +
                    ", progress=" + getPercent() + "%" +
                    '}';
        }
    }

    @Getter
    class OnlineUser<T extends Number> extends BaseUser<T> {

        private final Player player;
        private final String name;

        transient OfflinePlayer offline;

        OnlineUser(BaseSystem<T> system, Player player) {
            super(system, player.getUniqueId());
            this.name = (this.player = player).getName();
            offline = Bukkit.getOfflinePlayer(getUuid());
        }

        @Override
        public boolean isOnline() {
            return true;
        }
    }

    @Getter
    class OfflineUser<T extends Number> extends BaseUser<T> {

        transient OfflinePlayer offline;

        private Player player;
        private final String name;

        OfflineUser(BaseSystem<T> system, OfflinePlayer offline) {
            super(system, offline.getUniqueId());
            this.name = (this.offline = offline).getName();
        }

        @Override
        public boolean isOnline() {
            return false;
        }

        @NotNull
        public Player getPlayer() {
            if (player != null) return player;

            player = Bukkit.getPlayer(getUuid());
            if (player == null) {
                throw new IllegalStateException(
                    "Player " + name + " (" + getUuid() + ") is not online. " +
                    "Cannot perform operations that require an online player."
                );
            }

            return player;
        }
    }
}
