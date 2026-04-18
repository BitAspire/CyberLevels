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

    final CyberLevels main;
    final Cache cache;

    private final AtomicBoolean leaderboardQueued = new AtomicBoolean(false);
    private final BaseSystem<N> system;
    private final Map<UUID, LevelUser<N>> users = new ConcurrentHashMap<>();

    GlobalTask autoSaveTask = null;
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
        LevelUser<N> user = system.createUser(uuid);

        Path file = new File(main.getDataFolder(), "player_data" + File.separator + uuid + ".clv").toPath();
        if (!Files.exists(file)) return null;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;

            line = reader.readLine();
            if (line == null) return null;
            user.setLevel(parseLong(line, system.getStartLevel(), uuid, "level"), false);

            line = reader.readLine();
            if (line == null) return null;
            user.setExp(parseExp(line, uuid), false, false, false);

            line = reader.readLine();
            long claimed = (line != null) ? parseLong(line, user.getLevel(), uuid, "highest rewarded level") : user.getLevel();
            setRewardLevel(user, claimed);

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

        if (database != null) {
            user = database.getUser(uuid);

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

        return new LoadResult(user, migrationMessage);
    }

    private void loadUserAsync(OfflinePlayer offline, boolean updateLeaderboard) {
        Player player = (offline instanceof Player) ? (Player) offline : null;

        UUID uuid = offline.getUniqueId();
        LevelUser<N> user = users.get(uuid);

        if (user != null && player != null && !user.isOnline()) {
            LevelUser<N> newUser = system.createUser(uuid);

            newUser.setLevel(user.getLevel(), false);
            newUser.setExp(user.getExp() + "", true, false, false);
            setRewardLevel(newUser, getRewardLevel(user));

            users.put(uuid, newUser);
            if (updateLeaderboard) scheduleLeaderboardUpdate();
            return;
        }

        main.scheduler().runTaskAsynchronously(() -> {
            LoadResult result = loadUserData(uuid);
            main.scheduler().runTask(() -> finishUserLoad(uuid, player, result, updateLeaderboard));
        });
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
        online.setLevel(source.getLevel(), false);
        online.setExp(source.getExp() + "", true, false, false);
        setRewardLevel(online, getRewardLevel(source));
        return online;
    }

    private void finishUserLoad(UUID uuid, Player player, LoadResult result, boolean updateLeaderboard) {
        if (StringUtils.isNotBlank(result.migrationMessage))
            main.logger("Migrated " + (player != null ? player.getName() : uuid) + result.migrationMessage);

        LevelUser<N> existing = users.get(uuid);
        if (existing != null) {
            if (player != null && !existing.isOnline())
                users.put(uuid, toOnlineUser(uuid, existing));

            if (updateLeaderboard) scheduleLeaderboardUpdate();
            return;
        }

        LevelUser<N> loaded = result.user;
        if (player != null && !loaded.isOnline()) loaded = toOnlineUser(uuid, loaded);

        users.put(uuid, loaded);
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
            return;
        }

        try {
            LevelUser<N> offline = system.createOffline(uuid);
            offline.setLevel(user.getLevel(), false);
            offline.setExp(user.getExp(), false, false, false);

            setRewardLevel(offline, getRewardLevel(user));

            users.put(uuid, offline);
        }
        catch (Exception e) {
            users.remove(uuid);
            main.logger("&cNot able to convert to OfflineUser for: " + user.getName() + ". Deleting cache...");
            e.printStackTrace();
        }
    }

    @Override
    public void saveUser(LevelUser<N> user) {
        if (database != null) {
            database.updateUser(user);
            return;
        }
        saveToFlatFile(user);
    }

    private void saveUserSync(LevelUser<N> user) {
        if (database != null) {
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

    void saveOnlinePlayersSync(boolean clearData) {
        Bukkit.getOnlinePlayers().forEach(p -> savePlayer(p, clearData, true));
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
        if (autoSaveTask == null) return;
        autoSaveTask.cancel();
        autoSaveTask = null;
    }
}
