package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.cache.Cache;
import com.bitaspire.cyberlevels.cache.Config;
import com.bitaspire.cyberlevels.cache.Lang;
import com.bitaspire.cyberlevels.user.Database;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.bitaspire.cyberlevels.user.UserManager;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class UserManagerImpl<N extends Number> implements UserManager<N> {

    final CyberLevels main;
    final Cache cache;

    private final BaseSystem<N> system;
    private final Map<UUID, LevelUser<N>> users = new ConcurrentHashMap<>();

    BukkitTask autoSaveTask = null;
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
            if (player == null) player = Bukkit.getOfflinePlayer(uuid);
            loadUser(player);
            return users.get(uuid);
        }
        return user;
    }

    @Override
    public LevelUser<N> getUser(String name) {
        for (LevelUser<N> user : users.values())
            try {
                String n = Objects.requireNonNull(user.getName());
                if (n.equalsIgnoreCase(name)) {
                    // Check if user is stored as OfflineUser but is actually online
                    if (!user.isOnline()) {
                        Player online = Bukkit.getPlayer(user.getUuid());
                        if (online != null && online.isOnline()) {
                            // Player is online but we have OfflineUser in cache - reload as OnlineUser
                            main.logger("&eDetected online player " + name + " cached as OfflineUser. Reloading...");
                            loadPlayer(online);
                            return users.get(user.getUuid());
                        }
                    }
                    return user;
                }
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

    private LevelUser<N> loadFromFlatFile(UUID uuid) {
        LevelUser<N> user = system.createUser(uuid);

        Path file = new File(main.getDataFolder(), "player_data" + File.separator + uuid + ".clv").toPath();
        if (!Files.exists(file)) return null;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;

            line = reader.readLine();
            if (line == null) return null;
            user.setLevel(Long.parseLong(line.trim()), false);

            line = reader.readLine();
            if (line == null) return null;
            user.setExp(line.trim(), false, false, false);

            line = reader.readLine();
            long claimed = (line != null) ? Long.parseLong(line.trim()) : user.getLevel();
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

    private void loadUser(OfflinePlayer offline) {
        Player player = (offline instanceof Player) ? (Player) offline : null;

        UUID uuid = offline.getUniqueId();
        LevelUser<N> user = users.get(uuid);

        if (user != null && player != null && !user.isOnline()) {
            LevelUser<N> newUser = system.createUser(uuid);

            newUser.setLevel(user.getLevel(), false);
            newUser.setExp(user.getExp() + "", true, false, false);
            setRewardLevel(newUser, getRewardLevel(user));

            users.put(uuid, newUser);
            return;
        }

        String migrationMessage = "";

        if (database != null) {
            user = (player != null) ? database.getUser(player) : database.getUser(uuid);

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
                    LevelUser<?> oldUser = (player != null) ? old.getUser(player) : old.getUser(uuid);
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

        if (StringUtils.isNotBlank(migrationMessage))
            main.logger("Migrated " + (player != null ? player.getName() : uuid) + migrationMessage);

        users.put(uuid, user);
        system.updateLeaderboard();
    }

    @Override
    public void loadPlayer(OfflinePlayer offline) {
        loadUser(offline);
    }

    @Override
    public void loadPlayer(Player player) {
        loadUser(player);
    }

    @Override
    public void savePlayer(Player player, boolean clearData) {
        LevelUser<N> user = users.get(player.getUniqueId());
        if (user == null) return;

        saveUser(user);
        if (!clearData) return;

        UUID uuid = user.getUuid();

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

        int counter = 0;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            loadPlayer(player);
            counter++;
        }

        if (counter < 1) return;

        main.logger("&7Loaded data for &e" + counter +
                " &7offline player(s) in &a" +
                (System.currentTimeMillis() - l) +
                "ms&7.", "");
    }

    @Override
    public void loadOnlinePlayers() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        long l = System.currentTimeMillis();
        main.logger("&dLoading data for online players...");

        int counter = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player);
            counter++;
        }

        if (counter < 1) return;

        main.logger("&7Loaded data for &e" + counter +
                " &7online player(s) in &a" +
                (System.currentTimeMillis() - l) +
                "ms&7.", "");
    }

    @Override
    public void saveOnlinePlayers(boolean clearData) {
        Bukkit.getOnlinePlayers().forEach(p -> savePlayer(p, clearData));
    }

    @Override
    public void startAutoSave() {
        if (!cache.config().isAutoSaveEnabled()) return;
        Config config = cache.config();

        autoSaveTask = (new BukkitRunnable() {
            @Override
            public void run() {
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
            }
        }).runTaskLater(main, 20L * config.getAutoSaveInterval());
    }

    @Override
    public void cancelAutoSave() {
        if (autoSaveTask == null) return;
        autoSaveTask.cancel();
        autoSaveTask = null;
    }
}
