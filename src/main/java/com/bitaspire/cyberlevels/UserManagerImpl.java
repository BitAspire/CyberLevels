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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

final class UserManagerImpl<N extends Number> implements UserManager<N> {

    final CyberLevels main;
    final Cache cache;

    private final BaseSystem<N> system;
    private final Map<UUID, LevelUser<N>> users = new HashMap<>();

    BukkitTask autoSaveTask = null;
    @Getter
    private Database<N> database = null;

    UserManagerImpl(CyberLevels main, BaseSystem<N> system) {
        cache = (this.main = main).cache();
        (this.system = system).setUserManager(this);

        if (!cache.config().database().isEnabled())
            return;

        database = DatabaseFactory.createDatabase(main, system);
        database.connect();
    }

    @NotNull
    public Set<LevelUser<N>> getUsers() {
        return new HashSet<>(users.values());
    }

    @Override
    public LevelUser<N> getUser(UUID uuid) {
        LevelUser<N> user = users.get(uuid);
        if (user == null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                loadPlayer(player);

            return users.get(uuid);
        }

        return user;
    }

    private LevelUser<N> loadFromFlatFile(Player player) {
        final LevelUser<N> user = system.createUser(player);
        UUID uuid = user.getUuid();

        String basePath = File.separator + "player_data";
        String uuidPath = uuid + ".clv";

        File file = new File(main.getDataFolder() + basePath, uuidPath);
        try {
            if (file.exists()) {
                final Scanner scanner = new Scanner(file);

                user.setLevel(Long.parseLong(scanner.nextLine()), false);
                user.setExp(scanner.nextLine(), false, false);

                if (scanner.hasNext())
                    user.setMaxLevel(Long.parseLong(scanner.nextLine()));

                scanner.close();
                return user;
            }
        } catch (Exception e) {
            main.logger("&cFailed to load flat-file data for " + player.getName() + ".");
            e.printStackTrace();
        }
        return null;
    }

    void checkMigration() {
        final Database<?> old = main.database;
        if ((old == null || database == null) || old.getClass().equals(database.getClass()))
            return;

        main.logger("&eDetected database type change from " +
                old.getClass().getSimpleName() + " to " +
                database.getClass().getSimpleName() + ". Starting migration...");

        long start = System.currentTimeMillis();
        int migrated = 0;

        try {
            for (UUID uuid : old.getUuids()) {
                final LevelUser<?> user = old.getUser(uuid);
                if (user == null) continue;

                database.addUser(system.createUser(user), false);
                migrated++;
            }

            if (migrated > 0) {
                main.logger("&aMigrated " + migrated + " users in " + (System.currentTimeMillis() - start) + "ms.");
                return;
            }

            main.logger("&eNo players were found to migrated. Ending migration...");
        }
        catch (Exception e) {
            main.logger("&cMigration failed.");
            e.printStackTrace();
        }
    }

    @Override
    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        LevelUser<N> user;

        String migrationMessage = "";

        if (database != null) {
            user = database.getUser(player);

            if (user == null) {
                user = loadFromFlatFile(player);

                if (user != null) {
                    migrationMessage = " from flat-file to " + database.getClass().getSimpleName();
                    database.addUser(user, false);
                }
                else user = system.createUser(player);
            }
        } else {
            user = loadFromFlatFile(player);

            if (database != null) {
                migrationMessage = " from " + database.getClass().getSimpleName() + " to flat-file";
                if (user == null && (user = database.getUser(player)) != null) saveUser(user);
            }

            if (user == null) user = system.createUser(player);
        }

        if (StringUtils.isNotBlank(migrationMessage))
            main.logger("Migrated " + player.getName() + " " + migrationMessage);

        users.put(uuid, user);
    }

    @Override
    public void savePlayer(Player player, boolean clearData) {
        LevelUser<N> user = users.get(player.getUniqueId());
        if (user == null) return;

        saveUser(user);
        if (clearData) users.remove(user.getUuid());
    }

    @Override
    public void saveUser(LevelUser<N> user) {
        if (database != null) {
            database.updateUser(user);
            return;
        }

        File folder = new File(main.getDataFolder(), "player_data");
        if (!folder.exists() && !folder.mkdirs()) {
            main.logger("&cFailed to create player_data folder.");
            return;
        }

        File file = new File(folder, user.getUuid() + ".clv");

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
            writer.write(user.getLevel() + "\n");
            writer.write(user.getRoundedExp() + "\n");
            writer.write(user.getMaxLevel() + "");
        } catch (Exception e) {
            main.logger("&cFailed to save data for UUID " + user.getUuid() + ".");
            e.printStackTrace();
        }
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

        main.logger("&7Loaded data for &e" + counter +
                " &7online player(s) in &a" +
                (System.currentTimeMillis() - l) +
                "ms&7.", ""
        );
    }

    @Override
    public void saveOnlinePlayers(boolean clearData) {
        Bukkit.getOnlinePlayers().forEach(p -> savePlayer(p, clearData));
    }

    void updateMaxLevelToAll() {
        int count = 0;
        for (LevelUser<?> user : users.values()) {

        }
        users.values().forEach(u -> u.setMaxLevel(system.getMaxLevel()));
        saveOnlinePlayers(false);
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
                            start - System.currentTimeMillis()
                    );

                startAutoSave();
            }
        }).runTaskLater(main, 20L * config.getAutoSaveInterval());
    }

    @Override
    public void cancelAutoSave() {
        if (autoSaveTask == null)
            return;

        autoSaveTask.cancel();
        autoSaveTask = null;
    }
}
