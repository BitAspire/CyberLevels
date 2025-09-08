package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.user.UserManager;
import com.bitaspire.libs.formula.expression.ExpressionBuilder;
import com.bitaspire.cyberlevels.cache.Cache;
import com.bitaspire.cyberlevels.cache.Lang;
import com.bitaspire.cyberlevels.cache.Levels;
import com.bitaspire.cyberlevels.level.*;
import com.bitaspire.cyberlevels.user.LevelUser;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
abstract class BaseSystem<N extends Number> implements LevelSystem<N> {

    final CyberLevels main;
    final Cache cache;

    private final long startLevel, maxLevel;
    private final int startExp;

    private final Formula<N> formula;

    private final Map<Long, Level<N>> levels = new HashMap<>();
    private final Map<Long, Formula<N>> formulas = new HashMap<>();

    DecimalFormatter<N> formatter = null;
    UserManager<N> userManager = null;

    BaseLeaderboard<N> leaderboard = null;

    BaseSystem(CyberLevels main) {
        this.main = main;

        long l = System.currentTimeMillis();
        main.logger("&dLoading level data...");

        cache = main.cache();

        startExp = cache.levels().getStartExp();
        startLevel = cache.levels().getStartLevel();
        maxLevel = cache.levels().getMaxLevel();

        formula = createFormula(null);

        long start = startLevel;
        while (start <= maxLevel) {
            levels.put(start, new BaseLevel<>(this, start));
            start++;
        }

        if (cache.config().isRoundingEnabled())
            this.formatter = new DecimalFormatter<>(this);

        main.logger("&7Loaded &e" + (start - 1) + "&7 levels in &a" + (System.currentTimeMillis() - l) + "ms&7.", "");
    }

    @NotNull
    public Set<Level<N>> getLevels() {
        return new LinkedHashSet<>(levels.values());
    }

    @Override
    public Level<N> getLevel(long level) {
        return levels.get(level);
    }

    abstract Formula<N> createFormula(Long level);

    @Override
    public Formula<N> getCustomFormula(long level) {
        return formulas.get(level);
    }

    @NotNull
    public String roundDecimalAsString(N amount) {
        return formatter != null ? formatter.format(amount) : getOperator().toString(amount);
    }

    @NotNull
    public N roundDecimal(N amount) {
        return formatter != null ? getOperator().valueOf(formatter.format(amount)) : amount;
    }

    @NotNull
    public String replacePlaceholders(String string, Player player, boolean safeForFormula) {
        LevelUser<N> data = userManager.getUser(player);

        String[] keys = {"{level}", "{playerEXP}", "{nextLevel}",
                "{maxLevel}", "{minLevel}", "{minEXP}"};
        String[] values = {
                String.valueOf(data.getLevel()),
                data.getRoundedExp().toString(),
                String.valueOf(data.getLevel() + 1),
                String.valueOf(maxLevel),
                String.valueOf(startLevel),
                String.valueOf(startExp)
        };
        string = StringUtils.replaceEach(string, keys, values);

        String[] k = {"{player}", "{playerDisplayName}", "{playerUUID}"};
        String[] v = {
                player.getName(), player.getDisplayName(),
                player.getUniqueId().toString()
        };
        string = StringUtils.replaceEach(string, k, v);

        if (!safeForFormula) {
            k = new String[] {"{requiredEXP}", "{percent}", "{progressBar}"};
            v = new String[] {
                    data.getRoundedRequiredExp().toString(),
                    data.getPercent(), data.getProgressBar()
            };
            string = StringUtils.replaceEach(string, k, v);
        }

        return string;
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
    abstract LevelUser<N> createUser(Player player);

    @NotNull
    LevelUser<N> createUser(LevelUser<?> user) {
        LevelUser<N> newUser = createUser(user.getPlayer());

        newUser.setLevel(user.getLevel(), false);
        newUser.setExp(user.getExp() + "", true, false, false);
        newUser.setMaxLevel(user.getMaxLevel());

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

    static class BaseFormula<T extends Number> implements Formula<T> {

        private final BaseSystem<T> system;

        @Getter
        private final String asString;
        private final ExpressionBuilder<T> expression;

        BaseFormula(BaseSystem<T> system, Long custom, ExpressionBuilder<T> expression) {
            this.system = system;
            final Levels levels = system.cache.levels();

            String raw = levels.getCustomFormula(custom);
            asString = raw != null ? raw : levels.getFormula();

            this.expression = expression;
        }

        @NotNull
        public T evaluate(Player player) {
            return expression.build(system.replacePlaceholders(asString, player, true)).evaluate();
        }
    }

    @Getter
    static class BaseLevel<T extends Number> implements Level<T> {

        private final long level;
        private final Formula<T> formula;

        BaseLevel(BaseSystem<T> system, long level) {
            this.level = level;

            Formula<T> custom = system.createFormula(level);
            if (custom != null) {
                system.formulas.put(level, formula = custom);
                return;
            }

            formula = system.getFormula();
        }

        private final List<Reward> rewards = new ArrayList<>();

        @Override
        public void addReward(Reward reward) {
            rewards.add(reward);
        }

        @Override
        public void clearRewards() {
            rewards.clear();
        }

        @Override
        public T getRequiredExp(LevelUser<T> user) {
            return formula.evaluate(user.getPlayer());
        }

        @Override
        public T getRequiredExp(Player player) {
            return formula.evaluate(player);
        }
    }

    @Getter
    abstract class BaseLeaderboard<T extends Number> implements Leaderboard<T> {

        private final UserManager<T> userManager;

        private boolean updating = false;
        private final List<Entry<T>> topTenPlayers = new ArrayList<>();

        BaseLeaderboard(UserManager<T> manager) {
            this.userManager = manager;
        }

        @NotNull
        public List<LevelUser<T>> getTopTenPlayers() {
            return topTenPlayers.stream().map(Entry::getUser).collect(Collectors.toList());
        }

        @Override
        public void update() {
            List<LevelUser<T>> users = new ArrayList<>(userManager.getUsers());
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

        public void updateInstant(LevelUser<T> user) {
            Entry<T> entry = toEntry(user);

            Bukkit.getScheduler().runTaskAsynchronously(main, () -> {
                synchronized (topTenPlayers) {
                    topTenPlayers.removeIf(e -> e.getUuid().equals(entry.getUuid()));

                    int i = 0;
                    while (i < topTenPlayers.size() && entry.compareTo(topTenPlayers.get(i)) > 0) {
                        i++;
                    }

                    if (i >= 10) return;

                    topTenPlayers.add(i, entry);
                    if (topTenPlayers.size() > 10) topTenPlayers.remove(10);
                }
            });
        }

        @Override
        public void updateInstant(Player player) {
            updateInstant(userManager.getUser(player));
        }

        @Override
        public LevelUser<T> getTopPlayer(int position) {
            return updating || position < 1 || position > 10 ? null : userManager.getUser(topTenPlayers.get(position - 1).getUuid());
        }

        @Override
        public int checkPosition(Player player) {
            UUID uuid = player.getUniqueId();
            for (int i = 0; i < topTenPlayers.size(); i++) {
                if (uuid.equals(topTenPlayers.get(i).getUuid())) {
                    return i + 1;
                }
            }
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

    @Getter
    abstract class BaseUser<T extends Number> implements LevelUser<T> {

        private final BaseSystem<T> system;
        private final Operator<T> operator;

        private final Player player;
        private final UUID uuid;

        transient OfflinePlayer offline;

        long level;
        @Setter
        long maxLevel;

        T exp, lastAmount;
        long lastTime = 0L;

        BaseUser(BaseSystem<T> system, Player player) {
            uuid = (this.player = player).getUniqueId();
            exp = (this.operator = (this.system = system).getOperator()).fromDouble(getStartExp());
            level = system.getStartLevel();
            maxLevel = system.getMaxLevel();
            lastAmount = operator.zero();
        }

        abstract void checkLeaderboard();

        void sendLevelReward(long level) {
            if (cache.config().preventDuplicateRewards() && level <= maxLevel)
                return;

            BaseSystem.this.getLevel(level)
                    .getRewards()
                    .forEach(reward -> reward.giveAll(getPlayer()));
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

            if (sendMessage) {
                long diff = level - oldLevel;
                if (diff > 0) {
                    cache.lang().sendMessage(getPlayer(), Lang::getGainedLevels, "gainedLevels", diff);
                } else if (diff < 0) {
                    cache.lang().sendMessage(getPlayer(), Lang::getLostLevels, "lostLevels", Math.abs(diff));
                }
            }

            checkLeaderboard();
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
            long target = Math.max(level - Math.max(amount, 0), getStartExp());
            updateLevel(target, true, false);
        }

        private void changeExp(T amount, T difference, boolean sendMessage, boolean doMultiplier) {
            if (operator.compare(amount, operator.zero()) == 0) return;

            if (doMultiplier && operator.compare(amount, operator.zero()) > 0 && hasParentPerm("CyberLevels.player.multiplier.", false)) {
                amount = operator.multiply(amount, operator.fromDouble(getMultiplier()));
            }

            final T totalAmount = amount;
            long levelsChanged = 0;

            if (operator.compare(amount, operator.zero()) > 0) {
                while (operator.compare(operator.add(exp, amount), getRequiredExp()) >= 0) {
                    if (level == getMaxLevel()) {
                        exp = operator.zero();
                        return;
                    }

                    amount = operator.add(operator.subtract(amount, getRequiredExp()), exp);
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
                        exp = getRequiredExp();
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

            if (sendMessage) {
                T diff = operator.subtract(Objects.equals(displayTotal, operator.zero()) ? operator.zero() : displayTotal, difference);

                if (operator.compare(diff, operator.zero()) > 0) {
                    cache.lang().sendMessage(
                            getPlayer(), Lang::getGainedExp, new String[]{"gainedEXP", "totalGainedEXP"},
                            system.roundDecimalAsString(diff), system.roundDecimalAsString(totalAmount)
                    );
                } else if (operator.compare(diff, operator.zero()) < 0) {
                    cache.lang().sendMessage(
                            getPlayer(), Lang::getLostExp, new String[]{"lostEXP", "totalLostEXP"},
                            system.roundDecimalAsString(operator.abs(diff)), system.roundDecimalAsString(operator.abs(totalAmount))
                    );
                }

                if (levelsChanged > 0) {
                    cache.lang().sendMessage(getPlayer(), Lang::getGainedLevels, "gainedLevels", levelsChanged);
                } else if (levelsChanged < 0) {
                    cache.lang().sendMessage(getPlayer(), Lang::getLostLevels, "lostLevels", Math.abs(levelsChanged));
                }
            }

            lastAmount = displayTotal;
            lastTime = System.currentTimeMillis();

            level = Math.max(getStartLevel(), Math.min(level, getMaxLevel()));
            if (operator.compare(exp, operator.zero()) < 0) exp = operator.zero();

            if (sendMessage) checkLeaderboard();
        }

        public void addExp(T amount, boolean doMultiplier) {
            changeExp(amount, operator.zero(), true, doMultiplier);
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
                changeExp(amount, oldExp, sendMessage, false);
            } else {
                this.exp = amount;
            }
            if (checkLeaderboard) checkLeaderboard();
        }

        @Override
        public void setExp(String amount, boolean checkLevel, boolean sendMessage, boolean checkLeaderboard) {
            setExp(operator.valueOf(amount), checkLevel, sendMessage, checkLeaderboard);
        }

        public void removeExp(T amount) {
            T positive = operator.max(amount, operator.zero());
            T negative = operator.negate(positive);
            changeExp(negative, operator.zero(), true, false);
        }

        @Override
        public void removeExp(String amount) {
            removeExp(operator.valueOf(amount));
        }

        @NotNull
        public T getRoundedExp() {
            return system.roundDecimal(exp);
        }

        @NotNull
        public T getRequiredExp() {
            final Level<T> level = system.getLevel(this.level);
            return level != null ? level.getRequiredExp(this) : operator.zero();
        }

        @NotNull
        public T getRoundedRequiredExp() {
            return system.roundDecimal(getRequiredExp());
        }

        @NotNull
        public T getRemainingExp() {
            return operator.subtract(getRequiredExp(), exp);
        }

        @NotNull
        public T getRoundedRemainingExp() {
            return system.roundDecimal(getRemainingExp());
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
        public OfflinePlayer getOffline() {
            return offline == null ? (offline = Bukkit.getOfflinePlayer(uuid)) : offline;
        }

        @Override
        public boolean hasParentPerm(String permission, boolean checkOp) {
            if (checkOp && player.isOp()) return true;

            for (PermissionAttachmentInfo node : player.getEffectivePermissions()) {
                if (!node.getValue()) continue;
                if (node.getPermission().toLowerCase().startsWith(permission.toLowerCase()))
                    return true;
            }

            return false;
        }

        @Override
        public double getMultiplier() {
            double multiplier = 0;

            for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
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
                    "player=" + player.getName() +
                    ", uuid=" + uuid +
                    ", level=" + level +
                    ", exp=" + getRoundedExp() +
                    ", progress=" + getPercent() + "%" +
                    '}';
        }
    }
}
