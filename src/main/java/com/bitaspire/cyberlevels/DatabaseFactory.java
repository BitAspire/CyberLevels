package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.cache.Config;
import com.bitaspire.cyberlevels.user.Database;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;

@UtilityClass
class DatabaseFactory {

    abstract class DatabaseImpl<N extends Number> implements Database<N> {

        final CyberLevels main;
        final BaseSystem<N> system;

        final String type;
        HikariDataSource dataSource;

        DatabaseImpl(CyberLevels main, BaseSystem<N> system, String type) {
            this.main = main;
            this.system = system;
            this.type = type;
        }

        @Override
        public boolean isConnected() {
            return dataSource != null && !dataSource.isClosed();
        }

        abstract String getTable();

        void makeTable() {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS `" + getTable() + "` " +
                                 "(`UUID` VARCHAR(36), " +
                                 "`LEVEL` BIGINT(20), " +
                                 "`EXP` " + (main.cache().config().useBigDecimalSystem() ? "DECIMAL" : "DOUBLE") + "(20, 10), " +
                                 "`MAX_LEVEL` BIGINT(20))"
                 )
            ) {
                statement.executeUpdate();
            } catch (Exception e) {
                main.logger("&cFailed to create a " + type + " table.");
                e.printStackTrace();
            }
        }

        abstract HikariConfig createConfig();

        @Override
        public void connect() {
            if (isConnected()) return;

            main.logger("&dAttempting to connect to " + type + "...");
            long l = System.currentTimeMillis();

            try {
                dataSource = new HikariDataSource(createConfig());
                makeTable();
                main.logger("&7Connected to &e" + type + "&7 successfully in &a" + (System.currentTimeMillis() - l) + "ms&7.", "");
            } catch (Exception e) {
                main.logger("&cThere was an issue connecting to " + type + " Database.", "");
                e.printStackTrace();
            }
        }

        @Override
        public void disconnect() {
            if (!isConnected()) return;

            main.logger("&dAttempting to disconnect from " + type + "...");
            long l = System.currentTimeMillis();

            try {
                dataSource.close();
                dataSource = null;
                main.logger("&7Disconnected from &e" + type + "&7 successfully in &a" + (System.currentTimeMillis() - l) + "ms&7.", "");
            } catch (Exception e) {
                main.logger("&cThere was an issue disconnecting from " + type + " Database.", "");
                e.printStackTrace();
            }
        }

        @Override
        public boolean isUserLoaded(LevelUser<N> user) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + getTable() + " WHERE UUID=?")) {
                statement.setString(1, user.getUuid().toString());
                return statement.executeQuery().next();
            } catch (Exception e) {
                main.logger("&cFailed to check if user exists in table.");
                e.printStackTrace();
            }
            return false;
        }

        @Override
        public void addUser(LevelUser<N> user, boolean defValues) {
            if (isUserLoaded(user)) return;

            String level = main.levelSystem().getStartLevel() + "";
            String exp = main.levelSystem().getStartExp() + "";

            if (!defValues) {
                level = user.getLevel() + "";
                exp = user.getRoundedExp() + "";
            }

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO " + getTable() + "(UUID,LEVEL,EXP,MAX_LEVEL) VALUES (?,?,?,?)"))
            {
                statement.setString(1, user.getUuid().toString());
                statement.setString(2, level);
                statement.setString(3, exp);
                statement.setString(4, level);
                statement.executeUpdate();
            } catch (Exception e) {
                main.logger("&cFailed to add user " + user.getPlayer().getName() + ".");
                e.printStackTrace();
            }
        }

        @Override
        public void updateUser(LevelUser<N> user) {
            if (!isUserLoaded(user)) addUser(user);

            UUID uuid = user.getUuid();
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement st = connection.prepareStatement("UPDATE " + getTable() + " SET LEVEL=? WHERE UUID=?"))
                {
                    st.setString(1, user.getLevel() + "");
                    st.setString(2, uuid.toString());
                    st.executeUpdate();
                }
                try (PreparedStatement st = connection.prepareStatement("UPDATE " + getTable() + " SET EXP=? WHERE UUID=?"))
                {
                    st.setString(1, user.getRoundedExp() + "");
                    st.setString(2, uuid.toString());
                    st.executeUpdate();
                }
                try (PreparedStatement st = connection.prepareStatement("UPDATE " + getTable() + " SET MAX_LEVEL=? WHERE UUID=?"))
                {
                    st.setString(1, user.getMaxLevel() + "");
                    st.setString(2, uuid.toString());
                    st.executeUpdate();
                }
            } catch (Exception e) {
                main.logger("&cFailed to update user " + user.getPlayer().getName() + ".");
                e.printStackTrace();
            }
        }

        @Override
        public void removeUser(UUID uuid) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM " + getTable() + " WHERE UUID=?"))
            {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            } catch (Exception e) {
                main.logger("&cFailed to remove user " + uuid + " from " + type + " database.");
                e.printStackTrace();
            }
        }

        @Override
        public LevelUser<N> getUser(Player player) {
            if (player == null) return null;

            UUID uuid = player.getUniqueId();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + getTable() + " WHERE UUID=?"))
            {
                statement.setString(1, uuid.toString());

                ResultSet results = statement.executeQuery();
                if (!results.next()) return null;

                LevelUser<N> user = system.createUser(player);
                user.setLevel(results.getLong("LEVEL"), false);

                Number exp = !main.cache().config().useBigDecimalSystem() ?
                        results.getDouble("EXP") :
                        results.getBigDecimal("EXP");
                user.setExp(exp + "", false, false, false);

                long maxLevel = results.getLong("MAX_LEVEL");
                if (maxLevel != 0) user.setMaxLevel(maxLevel);

                return user;
            } catch (Exception e) {
                main.logger("&cFailed to get player data for " + player.getName() + ".", "");
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public LevelUser<N> getUser(UUID uuid) {
            return getUser(Bukkit.getPlayer(uuid));
        }

        @NotNull
        public Set<UUID> getUuids() {
            Set<UUID> uuids = new LinkedHashSet<>();

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT UUID FROM " + getTable()))
            {
                ResultSet rs = statement.executeQuery();
                while (rs.next())
                    try {
                        uuids.add(UUID.fromString(rs.getString("UUID")));
                    } catch (Exception ignored) {}
            }
            catch (SQLException e) {
                main.logger("&cFailed to fetch UUIDs from " + type + ".");
                e.printStackTrace();
            }

            return uuids;
        }
    }

    class MySQL<N extends Number> extends DatabaseImpl<N> {

        final String ip, database, username, password, table;
        final int port;
        final boolean ssl;

        MySQL(CyberLevels main, BaseSystem<N> system) {
            super(main, system, "MySQL");
            Config.Database db = main.cache().config().database();
            this.ip = db.getHost();
            this.port = Integer.parseInt(db.getPort());
            this.database = db.getDatabase();
            this.username = db.getUsername();
            this.password = db.getPassword();
            this.ssl = db.isSsl();
            this.table = db.getTable();
        }

        @Override
        String getTable() {
            return table;
        }

        @Override
        HikariConfig createConfig() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + ip + ":" + port + "/" + database + "?useSSL=" + ssl + "&autoReconnect=true");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setPoolName("CLV-MySQL");
            return config;
        }
    }

    class SQLite<N extends Number> extends DatabaseImpl<N> {

        private final String filePath, table;

        SQLite(CyberLevels main, BaseSystem<N> system) {
            super(main, system, "SQLite");
            Config.Database db = main.cache().config().database();
            this.filePath = db.getSqliteFile();
            this.table = db.getTable();
        }

        @Override
        String getTable() {
            return table;
        }

        @Override
        HikariConfig createConfig() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + filePath);
            config.setMaximumPoolSize(1); // SQLite es single-threaded
            config.setPoolName("CLV-SQLite");
            return config;
        }

        @Override
        void makeTable() {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS `" + table + "` (" +
                                 "`UUID` TEXT, " +
                                 "`LEVEL` INTEGER, " +
                                 "`EXP` NUMERIC, " +
                                 "`MAX_LEVEL` INTEGER)"
                 )
            ) {
                statement.executeUpdate();
            } catch (SQLException e) {
                main.logger("&cFailed to create a SQLite table.");
                e.printStackTrace();
            }
        }
    }

    class PostgreSQL<N extends Number> extends DatabaseImpl<N> {

        final String ip, database, username, password, table;
        final int port;

        PostgreSQL(CyberLevels main, BaseSystem<N> system) {
            super(main, system, "PostgreSQL");
            Config.Database db = main.cache().config().database();
            this.ip = db.getHost();
            this.port = Integer.parseInt(db.getPort());
            this.database = db.getDatabase();
            this.username = db.getUsername();
            this.password = db.getPassword();
            this.table = db.getTable();
        }

        @Override
        String getTable() {
            return table;
        }

        @Override
        HikariConfig createConfig() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://" + ip + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setPoolName("CLV-Postgres");
            return config;
        }

        @Override
        void makeTable() {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS \"" + table + "\" (" +
                                 "\"UUID\" VARCHAR(36), " +
                                 "\"LEVEL\" BIGINT, " +
                                 "\"EXP\" NUMERIC(20,10), " +
                                 "\"MAX_LEVEL\" BIGINT)"
                 )
            ) {
                statement.executeUpdate();
            } catch (SQLException e) {
                main.logger("&cFailed to create a PostgreSQL table.");
                e.printStackTrace();
            }
        }

        @Override
        public void removeUser(UUID uuid) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM \"" + getTable() + "\" WHERE \"UUID\"=?"))
            {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                main.logger("&cFailed to remove user " + uuid + " from PostgreSQL.");
                e.printStackTrace();
            }
        }

        @NotNull
        public Set<UUID> getUuids() {
            Set<UUID> uuids = new LinkedHashSet<>();

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT \"UUID\" FROM \"" + getTable() + "\""))
            {
                ResultSet rs = statement.executeQuery();
                while (rs.next())
                    try {
                        uuids.add(UUID.fromString(rs.getString("UUID")));
                    } catch (Exception ignored) {}
            }
            catch (SQLException e) {
                main.logger("&cFailed to fetch UUIDs from " + type + ".");
                e.printStackTrace();
            }

            return uuids;
        }
    }

    static <N extends Number> Database<N> createDatabase(CyberLevels main, BaseSystem<N> system) {
        String type = main.cache().config().database().getType();

        switch (type.toUpperCase(Locale.ENGLISH)) {
            case "SQLITE":
                return new SQLite<>(main, system);

            case "POSTGRES":
            case "POSTGRESQL":
                return new PostgreSQL<>(main, system);

            case "MYSQL":
            case "MARIADB":
            default:
                return new MySQL<>(main, system);
        }
    }
}