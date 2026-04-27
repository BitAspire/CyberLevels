package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.cache.Cache;
import com.bitaspire.cyberlevels.cache.Config;
import com.bitaspire.cyberlevels.cache.Lang;
import com.bitaspire.cyberlevels.user.Database;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.bitaspire.cyberlevels.user.UserManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.bitaspire.libs.scheduler.GlobalRunnable;
import com.bitaspire.libs.scheduler.GlobalTask;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class UserManagerImpl<N extends Number> implements UserManager<N> {

    private static final long DATABASE_SYNC_INTERVAL_TICKS = 20L;
    private static final long LOCAL_OFFLINE_CACHE_TTL_MS = 15_000L;

    final CyberLevels main;
    final Cache cache;

    private final AtomicBoolean leaderboardQueued = new AtomicBoolean(false);
    private final AtomicBoolean databaseSyncInFlight = new AtomicBoolean(false);
    private final BaseSystem<N> system;
    private final Map<UUID, LevelUser<N>> users = new ConcurrentHashMap<>();
    private final Map<UUID, Long> localOfflineSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> knownDatabaseUpdatedAt = new ConcurrentHashMap<>();
    private volatile long lastObservedDatabaseUpdateAt = System.currentTimeMillis();

    GlobalTask autoSaveTask = null;
    GlobalTask databaseSyncTask = null;
    @Getter
    private Database<N> database = null;

    UserManagerImpl(CyberLevels main, BaseSystem<N> system) {
        cache = (this.main = main).cache();
        (this.system = system).setUserManager(this);

        if (cache.config().database().isEnabled()) {
            database = DatabaseFactory.createDatabase(main, system);
            database.connect();
        }
    }

    @NotNull
    public Set<LevelUser<N>> getUsers() {
        return new LinkedHashSet<>(users.values());
    }

    @NotNull
    public List<LevelUser<N>> getUsersList() {
        return new ArrayList<>(users.values());
    }

    void checkMigration() {
        final Database<?> old = main.database;
        if (old == null) return;

        final Database<N> now = database;
        if (now != null && old.getClass().equals(now.getClass())) return;

        main.logger("&eDetected database type change from " +
                old.getClass().getSimpleName() + " to " +
                (now == null ? "FlatFile" : now.getClass().getSimpleName()) + ". Starting migration...");

        long start = System.currentTimeMillis();
        int migrated = 0;

        try {
            for (UUID uuid : old.getUuids()) {
                LevelUser<?> srcUser = old.getUser(uuid);
                if (srcUser == null) continue;

                LevelUser<N> copy = system.createUser(srcUser);
                if (now != null) {
                    now.addUser(copy, false);
                } else {
                    saveUser(copy);
                }

                migrated++;
            }

            if (migrated > 0) {
                main.logger("&aMigrated " + migrated + " users in " + (System.currentTimeMillis() - start) + "ms.");
            } else {
                main.logger("&eNo players were found to migrate. Ending migration...");
            }
        } catch (Exception e) {
            main.logger("&cMigration failed.");
            e.printStackTrace();
        }
    }

    @Override
    public LevelUser<N> getUser(UUID uuid) {
        LevelUser<N> user = users.get(uuid);

        if (user == null) {
            OfflinePlayer player = Bukkit.getPlayer(uuid);
            if (player == null)
                player = Bukkit.getOfflinePlayer(uuid);
            return loadUser(player);
        }

        return user;
    }

    @Override
    public LevelUser<N> getUser(String name) {
        if (StringUtils.isBlank(name)) return null;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return getUser(online);

        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getName().equalsIgnoreCase(name)) return getUser(player);

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String offlineName = offline.getName();
            if (offlineName != null && offlineName.equalsIgnoreCase(name))
                return getUser(offline.getUniqueId());
        }

        for (LevelUser<N> user : users.values())
            try {
                String loadedName = Objects.requireNonNull(user.getName());
                if (loadedName.equalsIgnoreCase(name)) return user;
            } catch (Exception ignored) {}

        return null;
    }

    void setRewardLevel(LevelUser<N> user, long level) {
        try {
            user.getClass().getMethod("setHighestRewardedLevel", long.class).invoke(user, level);
        } catch (Exception ignored) {}
    }

    long getRewardLevel(LevelUser<N> user) {
        try {
            return (long) user.getClass().getMethod("getHighestRewardedLevel").invoke(user);
        } catch (Exception ignored) {
            return user.getLevel();
        }
    }

    private long parseLong(String raw, long fallback, UUID uuid, String field) {
        if (StringUtils.isBlank(raw)) return fallback;

        String value = raw.trim();
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            main.logger("&eInvalid " + field + " value '" + value + "' for " + uuid + " in flat-file. Using " + fallback + ".");
            return fallback;
        }
    }

    private String parseExp(String raw, UUID uuid) {
        String fallback = String.valueOf(system.getStartExp());
        if (StringUtils.isBlank(raw)) return fallback;

        String value = raw.trim();
        try {
            system.getOperator().valueOf(value);
            return value;
        } catch (Exception ignored) {
            main.logger("&eInvalid exp value '" + value + "' for " + uuid + " in flat-file. Using " + fallback + ".");
            return fallback;
        }
    }

    private LevelUser<N> loadFromFlatFile(UUID uuid) {
        Path file = new File(main.getDataFolder(), "player_data" + File.separator + uuid + ".clv").toPath();
        if (!Files.exists(file)) return null;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            LevelUser<N> user = system.createUser(uuid);
            String line;

            line = reader.readLine();
            if (line == null) return null;
            long level = parseLong(line, system.getStartLevel(), uuid, "level");

            line = reader.readLine();
            if (line == null) return null;
            String exp = parseExp(line, uuid);

            line = reader.readLine();
            long claimed = (line != null) ? parseLong(line, level, uuid, "highest rewarded level") : level;
            system.applyStoredState(user, level, exp, claimed);

            return user;
        } catch (Exception e) {
            main.logger("&cFailed to load flat-file data for " + uuid + ".");
            e.printStackTrace();
            return null;
        }
    }

    private void saveToFlatFile(LevelUser<N> user) {
        File folder = new File(main.getDataFolder(), "player_data");
        if (!folder.exists() && !folder.mkdirs()) return;

        Path file = new File(folder, user.getUuid() + ".clv").toPath();

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write(user.getLevel() + "\n");
            writer.write(user.getExp() + "\n");
            long claimed = getRewardLevel(user);
            writer.write(claimed + "\n");
        } catch (Exception e) {
            main.logger("&cFailed to save data for UUID " + user.getUuid() + ".");
            e.printStackTrace();
        }
    }

    private LoadResult loadUserData(UUID uuid) {
        LevelUser<N> user;
        String migrationMessage = "";
        long databaseUpdatedAt = 0L;

        if (database != null) {
            DatabaseFactory.DatabaseImpl<N> databaseImpl = databaseImpl();
            DatabaseFactory.DatabaseImpl.StoredUserData stored = databaseImpl != null ? databaseImpl.fetchUserData(uuid) : null;

            if (stored != null) {
                user = databaseImpl.toLevelUser(stored);
                databaseUpdatedAt = stored.updatedAt;
            } else {
                user = database.getUser(uuid);
            }

            if (user == null) {
                if ((user = loadFromFlatFile(uuid)) != null) {
                    migrationMessage = " from flat-file to " + database.getClass().getSimpleName();
                    try {
                        database.addUser(user, false);
                    } catch (Exception e) {
                        main.logger("&cFailed to migrate user to database: " + uuid);
                    }
                } else {
                    user = system.createUser(uuid);
                }
            }
        } else {
            user = loadFromFlatFile(uuid);

            if (user == null) {
                Database<?> old = main.database;
                if (old != null) {
                    LevelUser<?> oldUser = old.getUser(uuid);
                    if (oldUser != null) {
                        migrationMessage = " from " + old.getClass().getSimpleName() + " to flat-file";
                        LevelUser<N> copy = system.createUser(oldUser);
                        saveToFlatFile(copy);
                        user = copy;
                    }
                }
            }

            if (user == null) user = system.createUser(uuid);
        }

        return new LoadResult(user, migrationMessage, databaseUpdatedAt);
    }

    private void loadUserAsync(OfflinePlayer offline, boolean updateLeaderboard) {
        Player player = (offline instanceof Player) ? (Player) offline : null;

        UUID uuid = offline.getUniqueId();
        LevelUser<N> user = users.get(uuid);

        if (user != null && player != null && !user.isOnline()) {
            if (shouldReuseLocalOfflineSnapshot(uuid)) {
                LevelUser<N> newUser = system.createUser(uuid);
                system.applyStoredState(newUser, user.getLevel(), String.valueOf(user.getExp()), getRewardLevel(user));

                users.put(uuid, newUser);
                localOfflineSnapshots.remove(uuid);
                if (updateLeaderboard) scheduleLeaderboardUpdate();
                return;
            }

            users.remove(uuid, user);
        }

        main.scheduler().runTaskAsynchronously(() -> {
            LoadResult result = loadUserData(uuid);
            main.scheduler().runTask(() -> finishUserLoad(uuid, player, result, updateLeaderboard));
        });
    }

    private boolean shouldReuseLocalOfflineSnapshot(UUID uuid) {
        if (database == null) return true;

        Long savedAt = localOfflineSnapshots.get(uuid);
        return savedAt != null && System.currentTimeMillis() - savedAt <= LOCAL_OFFLINE_CACHE_TTL_MS;
    }

    private LevelUser<N> loadUser(OfflinePlayer offline) {
        UUID uuid = offline.getUniqueId();
        Player player = (offline instanceof Player) ? (Player) offline : null;
        LoadResult result = loadUserData(uuid);
        finishUserLoad(uuid, player, result, true);
        return users.getOrDefault(uuid, result.user);
    }

    private LevelUser<N> toOnlineUser(UUID uuid, LevelUser<N> source) {
        LevelUser<N> online = system.createUser(uuid);
        system.applyStoredState(online, source.getLevel(), String.valueOf(source.getExp()), getRewardLevel(source));
        return online;
    }

    private void finishUserLoad(UUID uuid, Player player, LoadResult result, boolean updateLeaderboard) {
        if (StringUtils.isNotBlank(result.migrationMessage))
            main.logger("Migrated " + (player != null ? player.getName() : uuid) + result.migrationMessage);

        LevelUser<N> existing = users.get(uuid);
        if (existing != null) {
            if (player != null && !existing.isOnline())
                users.put(uuid, toOnlineUser(uuid, existing));

            if (result.databaseUpdatedAt > 0L)
                knownDatabaseUpdatedAt.put(uuid, result.databaseUpdatedAt);
            if (player != null) localOfflineSnapshots.remove(uuid);
            if (updateLeaderboard) scheduleLeaderboardUpdate();
            return;
        }

        LevelUser<N> loaded = result.user;
        if (player != null && !loaded.isOnline()) loaded = toOnlineUser(uuid, loaded);

        users.put(uuid, loaded);
        if (result.databaseUpdatedAt > 0L)
            knownDatabaseUpdatedAt.put(uuid, result.databaseUpdatedAt);
        if (player != null) localOfflineSnapshots.remove(uuid);
        if (updateLeaderboard) scheduleLeaderboardUpdate();
    }

    private void scheduleLeaderboardUpdate() {
        if (!leaderboardQueued.compareAndSet(false, true)) return;
        main.scheduler().runTask(() -> {
            system.updateLeaderboard();
            leaderboardQueued.set(false);
        });
    }

    @RequiredArgsConstructor
    private class LoadResult {
        final LevelUser<N> user;
        final String migrationMessage;
        final long databaseUpdatedAt;
    }

    @Override
    public void loadPlayer(OfflinePlayer offline) {
        loadUserAsync(offline, true);
    }

    @Override
    public void loadPlayer(Player player) {
        loadUserAsync(player, true);
    }

    @Override
    public void savePlayer(Player player, boolean clearData) {
        savePlayer(player, clearData, false);
    }

    void savePlayerSync(Player player, boolean clearData) {
        savePlayer(player, clearData, true);
    }

    private void savePlayer(Player player, boolean clearData, boolean syncSave) {
        LevelUser<N> user = users.get(player.getUniqueId());
        if (user == null) return;

        if (syncSave) saveUserSync(user);
        else saveUserAsync(user);
        if (!clearData) return;

        UUID uuid = user.getUuid();

        if (syncSave) {
            users.remove(uuid);
            localOfflineSnapshots.remove(uuid);
            return;
        }

        try {
            LevelUser<N> offline = system.createOffline(uuid);
            system.applyStoredState(offline, user.getLevel(), String.valueOf(user.getExp()), getRewardLevel(user));

            users.put(uuid, offline);
            localOfflineSnapshots.put(uuid, System.currentTimeMillis());
        }
        catch (Exception e) {
            users.remove(uuid);
            localOfflineSnapshots.remove(uuid);
            main.logger("&cNot able to convert to OfflineUser for: " + user.getName() + ". Deleting cache...");
            e.printStackTrace();
        }
    }

    @Override
    public void saveUser(LevelUser<N> user) {
        if (database != null) {
            knownDatabaseUpdatedAt.put(user.getUuid(), System.currentTimeMillis());
            database.updateUser(user);
            return;
        }
        saveToFlatFile(user);
    }

    private void saveUserSync(LevelUser<N> user) {
        if (database != null) {
            knownDatabaseUpdatedAt.put(user.getUuid(), System.currentTimeMillis());
            database.updateUserSync(user);
            return;
        }
        saveToFlatFile(user);
    }

    private void saveUserAsync(LevelUser<N> user) {
        main.scheduler().runTaskAsynchronously(() -> saveUser(user));
    }

    @Override
    public void removeUser(UUID uuid) {
        users.remove(uuid);
        localOfflineSnapshots.remove(uuid);
        knownDatabaseUpdatedAt.remove(uuid);

        if (database != null) {
            database.removeUser(uuid);
            return;
        }

        File file = new File(main.getDataFolder() + File.separator + "player_data", uuid + ".clv");
        if (!file.exists()) return;

        if (!file.delete()) main.logger("&cFailed to delete flat-file for user " + uuid);
    }

    void loadOfflinePlayers() {
        if (Bukkit.getOfflinePlayers().length < 1) return;

        long l = System.currentTimeMillis();
        main.logger("&dLoading data for offline players...");

        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        if (players.length < 1) return;

        int batchSize = 10;

        new GlobalRunnable(main.scheduler()) {
            int index = 0;
            int loaded = 0;

            @Override
            public void run() {
                int processed = 0;
                while (index < players.length && processed < batchSize) {
                    loadUserAsync(players[index++], false);
                    loaded++;
                    processed++;
                }

                if (index < players.length) return;

                cancel();
                if (loaded > 0)
                    main.logger("&7Loaded data for &e" + loaded +
                            " &7offline player(s) in &a" +
                            (System.currentTimeMillis() - l) +
                            "ms&7.", "");

                scheduleLeaderboardUpdate();
            }
        }.runTaskTimer(0L, 1L);
    }

    @Override
    public void loadOnlinePlayers() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        long l = System.currentTimeMillis();
        main.logger("&dLoading data for online players...");

        int counter = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadUserAsync(player, false);
            counter++;
        }

        if (counter < 1) return;

        main.logger("&7Loaded data for &e" + counter +
                " &7online player(s) in &a" +
                (System.currentTimeMillis() - l) +
                "ms&7.", "");
        scheduleLeaderboardUpdate();
    }

    @Override
    public void saveOnlinePlayers(boolean clearData) {
        Bukkit.getOnlinePlayers().forEach(p -> savePlayer(p, clearData, false));
    }

    void saveOnlinePlayersSync() {
        Bukkit.getOnlinePlayers().forEach(p -> savePlayer(p, true, true));
    }

    @SuppressWarnings("unchecked")
    private DatabaseFactory.DatabaseImpl<N> databaseImpl() {
        return database instanceof DatabaseFactory.DatabaseImpl<?> ?
                (DatabaseFactory.DatabaseImpl<N>) database :
                null;
    }

    void startDatabaseSync() {
        if (databaseSyncTask != null || databaseImpl() == null) return;
        scheduleDatabaseSync();
    }

    private void scheduleDatabaseSync() {
        databaseSyncTask = main.scheduler().runTaskLaterAsynchronously(() -> {
            try {
                pollDatabaseUpdates();
            } finally {
                if (main.isEnabled() && databaseSyncTask != null)
                    scheduleDatabaseSync();
            }
        }, DATABASE_SYNC_INTERVAL_TICKS);
    }

    private void pollDatabaseUpdates() {
        DatabaseFactory.DatabaseImpl<N> databaseImpl = databaseImpl();
        if (databaseImpl == null || users.isEmpty()) return;
        if (!databaseSyncInFlight.compareAndSet(false, true)) return;

        try {
            List<DatabaseFactory.DatabaseImpl.StoredUserData> updates =
                    databaseImpl.getUsersUpdatedSince(lastObservedDatabaseUpdateAt);

            if (updates.isEmpty()) return;

            List<DatabaseFactory.DatabaseImpl.StoredUserData> relevant = new ArrayList<>();
            long watermark = lastObservedDatabaseUpdateAt;

            for (DatabaseFactory.DatabaseImpl.StoredUserData update : updates) {
                watermark = Math.max(watermark, update.updatedAt);

                if (!users.containsKey(update.uuid)) continue;

                long known = knownDatabaseUpdatedAt.getOrDefault(update.uuid, 0L);
                if (update.updatedAt > known)
                    relevant.add(update);
            }

            lastObservedDatabaseUpdateAt = watermark;
            if (relevant.isEmpty()) return;

            main.scheduler().runTask(() -> applyDatabaseUpdates(relevant));
        } finally {
            databaseSyncInFlight.set(false);
        }
    }

    private void applyDatabaseUpdates(List<DatabaseFactory.DatabaseImpl.StoredUserData> updates) {
        boolean changed = false;

        for (DatabaseFactory.DatabaseImpl.StoredUserData update : updates) {
            LevelUser<N> user = users.get(update.uuid);
            if (user == null) continue;

            long known = knownDatabaseUpdatedAt.getOrDefault(update.uuid, 0L);
            if (update.updatedAt <= known) continue;

            system.applyStoredState(user, update.level, update.exp, update.highestRewarded);
            knownDatabaseUpdatedAt.put(update.uuid, update.updatedAt);
            localOfflineSnapshots.remove(update.uuid);
            changed = true;
        }

        if (changed) scheduleLeaderboardUpdate();
    }

    @Override
    public void startAutoSave() {
        if (!cache.config().isAutoSaveEnabled()) return;
        Config config = cache.config();

        autoSaveTask = main.scheduler().runTaskLater(() -> {
            long start = System.currentTimeMillis();
            main.userManager().saveOnlinePlayers(false);

            if (config.syncLeaderboardOnAutoSave())
                system.getLeaderboard().update();

            if (config.isMessagesOnAutoSave())
                cache.lang().sendMessage(
                        null, Lang::getAutoSave, "ms",
                        System.currentTimeMillis() - start
                );

            startAutoSave();
        }, 20L * config.getAutoSaveInterval());
    }

    @Override
    public void cancelAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        if (databaseSyncTask != null) {
            databaseSyncTask.cancel();
            databaseSyncTask = null;
        }
    }
}
