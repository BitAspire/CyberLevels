package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.cache.Config;
import com.bitaspire.cyberlevels.user.Database;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.experimental.UtilityClass;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@UtilityClass
class DatabaseFactory {

    abstract static class DatabaseImpl<N extends Number> implements Database<N> {

        private static final String MYSQL_CHARSET = "utf8mb4";
        private static final String MYSQL_COLLATION = "utf8mb4_unicode_ci";

        final CyberLevels main;
        final BaseSystem<N> system;

        final String type;
        HikariDataSource dataSource;

        DatabaseImpl(CyberLevels main, BaseSystem<N> system, String type) {
            this.main = main;
            this.system = system;
            this.type = type;
        }

        abstract String getTable();
        abstract HikariConfig createConfig();

        String qCol(String name) {
            return name;
        }

        String qTab(String name) {
            return name;
        }

        abstract PreparedStatement prepareUpsert(Connection c, UUID uuid, long level, String exp, long updatedAt) throws SQLException;
        abstract PreparedStatement prepareUpsertMeta(Connection c, UUID uuid, long highestRewarded, long updatedAt) throws SQLException;

        abstract Set<String> getExistingColumns(Connection conn) throws SQLException;
        abstract boolean isExpColumnTextual(Connection conn) throws SQLException;
        abstract boolean hasPrimaryKeyOnUuid(Connection conn) throws SQLException;

        abstract void createTargetTable(Connection conn) throws SQLException;
        abstract void dropTableIfExists(Connection conn, String table) throws SQLException;
        abstract void renameTable(Connection conn, String from, String to) throws SQLException;

        String metaTable() {
            return getTable() + "_meta";
        }

        boolean isMySqlFamily() {
            return this instanceof MySQL;
        }

        String joinUuidOperand(String alias) {
            String column = alias + "." + qCol("UUID");
            if (!isMySqlFamily()) return column;

            return "CONVERT(" + column + " USING " + MYSQL_CHARSET + ") COLLATE " + MYSQL_COLLATION;
        }

        void ensureMetaSchema(Connection conn) throws SQLException {
            final boolean sqlite = (this instanceof SQLite);
            String idType = sqlite ? "TEXT" : "VARCHAR(36)";
            String longType = sqlite ? "INTEGER" : "BIGINT";
            String suffix = isMySqlFamily() ?
                    " CHARSET=" + MYSQL_CHARSET + " COLLATE=" + MYSQL_COLLATION :
                    "";

            String sql = "CREATE TABLE IF NOT EXISTS " + qTab(metaTable()) + " (" +
                    qCol("UUID") + " " + idType + " PRIMARY KEY," +
                    qCol("HIGHEST_REWARDED") + " " + longType + "," +
                    qCol("UPDATED_AT") + " " + longType + " NOT NULL DEFAULT 0" +
                    ")" + suffix;
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
            }
        }

        void ensureCollationCompatibility(Connection conn) {
            if (!isMySqlFamily()) return;

            ensureMySqlTableCollation(conn, getTable());
            ensureMySqlTableCollation(conn, metaTable());
        }

        private void ensureMySqlTableCollation(Connection conn, String table) {
            try {
                if (!tableExists(conn, table)) return;

                String current = currentMySqlTableCollation(conn, table);
                if (current != null && current.equalsIgnoreCase(MYSQL_COLLATION)) return;

                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(
                            "ALTER TABLE " + qTab(table) +
                                    " CONVERT TO CHARACTER SET " + MYSQL_CHARSET +
                                    " COLLATE " + MYSQL_COLLATION
                    );
                }

                main.logger("&e" + type + ": normalized collation for table '" + table + "' to " + MYSQL_COLLATION + ".");
            } catch (SQLException e) {
                main.logger("&e" + type + ": unable to normalize collation for table '" + table + "'. Queries will use a compatibility path instead.");
            }
        }

        private String currentMySqlTableCollation(Connection conn, String table) throws SQLException {
            String sql = "SELECT TABLE_COLLATION FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        }

        long readHighestRewarded(Connection conn, UUID uuid) {
            String sql = "SELECT " + qCol("HIGHEST_REWARDED") + " FROM " + qTab(metaTable()) +
                    " WHERE " + qCol("UUID") + "=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            } catch (SQLException ignored) {}
            return -1L;
        }

        @Override
        public boolean isConnected() {
            return dataSource != null && !dataSource.isClosed();
        }

        @Override
        public void connect() {
            if (isConnected()) return;

            main.logger("&dAttempting to connect to " + type + "...");
            long l = System.currentTimeMillis();

            try {
                dataSource = new HikariDataSource(createConfig());

                try (Connection conn = dataSource.getConnection()) {
                    ensureTargetSchema(conn);
                    ensureMetaSchema(conn);
                    ensureCollationCompatibility(conn);
                }

                main.logger("&7Connected to &e" + type + "&7 successfully in &a" + (System.currentTimeMillis() - l) + "ms&7.");
            } catch (Exception e) {
                main.logger("&cThere was an issue connecting to " + type + " Database.");
                e.printStackTrace();

                if (dataSource != null) {
                    try {
                        dataSource.close();
                    } catch (Exception ignored) {}
                    dataSource = null;
                }
            }
        }

        @Override
        public void disconnect() {
            if (!isConnected()) return;

            main.logger("&dAttempting to disconnect from " + type + "...");
            long l = System.currentTimeMillis();

            Runnable close = () -> {
                try {
                    dataSource.close();
                    main.logger("&7Disconnected from &e" + type + "&7 successfully in &a" + (System.currentTimeMillis() - l) + "ms&7.");
                } catch (Exception e) {
                    main.logger("&cThere was an issue disconnecting from " + type + " Database.");
                    e.printStackTrace();
                } finally {
                    dataSource = null;
                }
            };

            if (main.isEnabled()) {
                main.scheduler().runTaskAsynchronously(close);
            } else {
                close.run();
            }
        }

        void disconnectSync() {
            if (!isConnected()) return;

            main.logger("&dAttempting to disconnect from " + type + "...");
            long l = System.currentTimeMillis();

            try {
                dataSource.close();
                main.logger("&7Disconnected from &e" + type + "&7 successfully in &a" + (System.currentTimeMillis() - l) + "ms&7.");
            } catch (Exception e) {
                main.logger("&cThere was an issue disconnecting from " + type + " Database.");
                e.printStackTrace();
            } finally {
                dataSource = null;
            }
        }

        void ensureTargetSchema(Connection conn) throws SQLException {
            if (!tableExists(conn, getTable())) {
                createTargetTable(conn);
                return;
            }

            Set<String> cols = getExistingColumns(conn);
            boolean needMigration = !cols.contains("UUID") || !cols.contains("LEVEL") || !cols.contains("EXP");

            if (cols.contains("MAX_LEVEL") ||
                    !cols.contains("UPDATED_AT") ||
                    !isExpColumnTextual(conn) ||
                    !hasPrimaryKeyOnUuid(conn))
                needMigration = true;

            if (needMigration) migrateTableToCanonical(conn);
        }

        boolean tableExists(Connection conn, String table) throws SQLException {
            try (ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
                return rs.next();
            }
        }

        void migrateTableToCanonical(Connection conn) throws SQLException {
            String table = getTable();
            String backup = table + "_backup_" + System.currentTimeMillis();

            main.logger("&e" + type + ": migrating table '" + table + "' to canonical schema (backup: " + backup + ")...");

            conn.setAutoCommit(false);
            try (Statement ignored = conn.createStatement()) {
                renameTable(conn, table, backup);
                createTargetTable(conn);
                ensureMetaSchema(conn);

                Map<UUID, Row> bestByUuid = new LinkedHashMap<>();

                String selectSQL = "SELECT * FROM " + qTab(backup);
                try (PreparedStatement ps = conn.prepareStatement(selectSQL);
                     ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {
                        String uuidStr = safeGet(rs, "UUID");
                        if (uuidStr == null || uuidStr.isEmpty()) continue;

                        UUID uuid;
                        try {
                            uuid = UUID.fromString(uuidStr);
                        } catch (Exception e) {
                            continue;
                        }

                        long level = safeGetLong(rs, "LEVEL", 1L);

                        String expStr;
                        try {
                            expStr = rs.getString("EXP");
                            if (expStr == null) expStr = "0";
                        } catch (SQLException e) {
                            expStr = String.valueOf(safeGetDouble(rs));
                        }

                        long updated = safeGetLong(rs, "UPDATED_AT", 0L);
                        long maxLevel = safeGetLong(rs, "MAX_LEVEL", -1L);

                        Row row = new Row(uuid, level, expStr, updated, maxLevel);
                        Row prev = bestByUuid.get(uuid);
                        if (prev == null ||
                                row.updatedAt > prev.updatedAt ||
                                (row.updatedAt == prev.updatedAt && row.level > prev.level)) {
                            if (prev != null)
                                row.maxLevel = Math.max(row.maxLevel, prev.maxLevel);
                            bestByUuid.put(uuid, row);
                        } else {
                            prev.maxLevel = Math.max(prev.maxLevel, row.maxLevel);
                        }
                    }
                }

                String insertSQL = "INSERT INTO " + qTab(table) + " (" +
                        qCol("UUID") + "," + qCol("LEVEL") + "," + qCol("EXP") + "," + qCol("UPDATED_AT") +
                        ") VALUES (?,?,?,?)";

                try (PreparedStatement ins = conn.prepareStatement(insertSQL)) {
                    for (Row r : bestByUuid.values()) {
                        ins.setString(1, r.uuid.toString());
                        ins.setLong(2, r.level);
                        ins.setString(3, r.exp);
                        ins.setLong(4, r.updatedAt);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }

                for (Row r : bestByUuid.values()) {
                    long highest = (r.maxLevel >= 0 ? r.maxLevel : r.level);
                    try (PreparedStatement up = prepareUpsertMeta(conn, r.uuid, highest, r.updatedAt)) {
                        up.executeUpdate();
                    }
                }

                dropTableIfExists(conn, backup);

                conn.commit();
                main.logger("&a" + type + ": migration for '" + table + "' completed (" + bestByUuid.size() + " rows).");
            } catch (SQLException ex) {
                conn.rollback();
                main.logger("&c" + type + ": migration failed, attempting rollback restore...");
                try {
                    dropTableIfExists(conn, getTable());
                    renameTable(conn, backup, getTable());
                } catch (SQLException restoreEx) {
                    main.logger("&c" + type + ": failed to restore backup: " + restoreEx.getMessage());
                }
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        static class Row {
            final UUID uuid;
            final long level;
            final String exp;
            final long updatedAt;
            long maxLevel;

            Row(UUID uuid, long level, String exp, long updatedAt, long maxLevel) {
                this.uuid = uuid;
                this.level = level;
                this.exp = exp;
                this.updatedAt = updatedAt;
                this.maxLevel = maxLevel;
            }
        }

        static final class StoredUserData {
            final UUID uuid;
            final long level;
            final String exp;
            final long highestRewarded;
            final long updatedAt;

            StoredUserData(UUID uuid, long level, String exp, long highestRewarded, long updatedAt) {
                this.uuid = uuid;
                this.level = level;
                this.exp = exp;
                this.highestRewarded = highestRewarded;
                this.updatedAt = updatedAt;
            }
        }

        private String selectStoredUserSql(String whereClause) {
            return "SELECT t." + qCol("UUID") + " AS UUID," +
                    " t." + qCol("LEVEL") + " AS LEVEL," +
                    " t." + qCol("EXP") + " AS EXP," +
                    " t." + qCol("UPDATED_AT") + " AS UPDATED_AT," +
                    " m." + qCol("HIGHEST_REWARDED") + " AS META_HIGHEST_REWARDED," +
                    " m." + qCol("UPDATED_AT") + " AS META_UPDATED_AT " +
                    "FROM " + qTab(getTable()) + " t " +
                    "LEFT JOIN " + qTab(metaTable()) + " m ON " + joinUuidOperand("t") + " = " + joinUuidOperand("m") +
                    " " + whereClause;
        }

        private StoredUserData readStoredUserData(ResultSet rs) throws SQLException {
            UUID uuid = UUID.fromString(rs.getString("UUID"));
            long level = parseLevel(rs.getString("LEVEL"), uuid);
            String exp = parseExp(rs.getString("EXP"), uuid);

            long highest = safeGetLong(rs, "META_HIGHEST_REWARDED", level);
            if (highest < 0) highest = level;

            long updatedAt = Math.max(
                    safeGetLong(rs, "UPDATED_AT", 0L),
                    safeGetLong(rs, "META_UPDATED_AT", 0L)
            );

            return new StoredUserData(uuid, level, exp, highest, updatedAt);
        }

        StoredUserData fetchUserData(UUID uuid) {
            if (!isConnected() || uuid == null) return null;

            String sql = selectStoredUserSql("WHERE t." + qCol("UUID") + "=?");
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement st = connection.prepareStatement(sql)) {
                st.setString(1, uuid.toString());

                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) return null;
                    return readStoredUserData(rs);
                }
            } catch (Exception e) {
                main.logger("&cFailed to get player data for " + uuid + ".", "");
                e.printStackTrace();
                return null;
            }
        }

        List<StoredUserData> getUsersUpdatedSince(long updatedAfter) {
            if (!isConnected()) return Collections.emptyList();

            String sql = selectStoredUserSql(
                    "WHERE t." + qCol("UPDATED_AT") + " > ? OR m." + qCol("UPDATED_AT") + " > ?"
            );

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement st = connection.prepareStatement(sql)) {
                st.setLong(1, updatedAfter);
                st.setLong(2, updatedAfter);

                try (ResultSet rs = st.executeQuery()) {
                    List<StoredUserData> users = new ArrayList<>();
                    while (rs.next()) {
                        try {
                            users.add(readStoredUserData(rs));
                        } catch (Exception ignored) {}
                    }
                    return users;
                }
            } catch (Exception e) {
                main.logger("&cFailed to fetch recent player updates from " + type + ".", "");
                e.printStackTrace();
                return Collections.emptyList();
            }
        }

        LevelUser<N> toLevelUser(StoredUserData data) {
            if (data == null) return null;

            LevelUser<N> user = system.createUser(data.uuid);
            system.applyStoredState(user, data.level, data.exp, data.highestRewarded);
            return user;
        }

        static String safeGet(ResultSet rs, String col) {
            try {
                return rs.getString(col);
            } catch (SQLException e) {
                return null;
            }
        }

        static long safeGetLong(ResultSet rs, String col, long def) {
            try {
                String s = rs.getString(col);
                if (s == null) return def;
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException n) {
                    try {
                        return rs.getLong(col);
                    } catch (SQLException ignored) {
                        return def;
                    }
                }
            } catch (SQLException e) {
                try {
                    return rs.getLong(col);
                } catch (SQLException ex) {
                    return def;
                }
            }
        }

        static double safeGetDouble(ResultSet rs) {
            try {
                return rs.getDouble("EXP");
            } catch (SQLException e) {
                String s = safeGet(rs, "EXP");
                if (s == null) return 0.0;
                try {
                    return Double.parseDouble(s.trim());
                } catch (Exception ignored) {
                    return 0.0;
                }
            }
        }

        @Override
        public boolean isUserLoaded(LevelUser<N> user) {
            if (!isConnected()) return false;
            CompletableFuture<Boolean> future = new CompletableFuture<>();

            main.scheduler().runTaskAsynchronously(() -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "SELECT 1 FROM " + qTab(getTable()) + " WHERE " + qCol("UUID") + "=?")) {
                    statement.setString(1, user.getUuid().toString());
                    try (ResultSet rs = statement.executeQuery()) {
                        future.complete(rs.next());
                    }
                } catch (Exception e) {
                    main.logger("&cFailed to check if user exists in table.");
                    e.printStackTrace();
                    future.complete(false);
                }
            });

            try {
                return future.get();
            } catch (Exception e) {
                return false;
            }
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

        private long parseLevel(String raw, UUID uuid) {
            long fallback = system.getStartLevel();
            if (raw == null) return fallback;

            String value = raw.trim();
            if (value.isEmpty()) return fallback;

            try {
                return Long.parseLong(value);
            } catch (Exception ignored) {
                main.logger("&eInvalid level value '" + value + "' for " + uuid + " in " + type + " database. Using " + fallback + ".");
                return fallback;
            }
        }

        private String parseExp(String raw, UUID uuid) {
            String fallback = String.valueOf(system.getStartExp());
            if (raw == null) return fallback;

            String value = raw.trim();
            if (value.isEmpty()) return fallback;

            try {
                system.getOperator().valueOf(value);
                return value;
            } catch (Exception ignored) {
                main.logger("&eInvalid exp value '" + value + "' for " + uuid + " in " + type + " database. Using " + fallback + ".");
                return fallback;
            }
        }

        private void upsertUser(Connection connection, UUID uuid, long level, String expStr, long highest, long now) throws SQLException {
            try (PreparedStatement st = prepareUpsert(connection, uuid, level, expStr, now)) {
                st.executeUpdate();
            }

            try (PreparedStatement stm = prepareUpsertMeta(connection, uuid, highest, now)) {
                stm.executeUpdate();
            }
        }

        @Override
        public void addUser(LevelUser<N> user, boolean defValues) {
            if (!isConnected()) return;
            if (isUserLoaded(user)) return;

            String levelStr = String.valueOf(main.levelSystem().getStartLevel());
            String expStr = String.valueOf(main.levelSystem().getStartExp());

            if (!defValues) {
                levelStr = String.valueOf(user.getLevel());
                expStr = String.valueOf(user.getExp()); // already string-ish
            }

            final String finalLevelStr = levelStr;
            final String finalExpStr = expStr;

            main.scheduler().runTaskAsynchronously(() -> {
                String sql = "INSERT INTO " + qTab(getTable()) + " (" +
                        qCol("UUID") + "," + qCol("LEVEL") + "," + qCol("EXP") + "," + qCol("UPDATED_AT") +
                        ") VALUES (?,?,?,?)";

                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement st = connection.prepareStatement(sql))
                    {
                        st.setString(1, user.getUuid().toString());
                        st.setLong(2, Long.parseLong(finalLevelStr));
                        st.setString(3, finalExpStr);
                        st.setLong(4, System.currentTimeMillis());
                        st.executeUpdate();

                        long now = System.currentTimeMillis();
                        long highest = getRewardLevel(user);
                        try (PreparedStatement pm = prepareUpsertMeta(connection, user.getUuid(), highest, now)) {
                            pm.executeUpdate();
                        }
                    } catch (Exception e) {
                        main.logger("&cFailed to add user " + user.getName() + ".");
                        e.printStackTrace();
                    }
            });
        }

        @Override
        public void updateUser(LevelUser<N> user) {
            if (!isConnected()) return;
            final UUID uuid = user.getUuid();
            final long now = System.currentTimeMillis();
            final String expStr = String.valueOf(user.getExp());
            final long level = user.getLevel();
            final String name = user.getName();
            final long highest = getRewardLevel(user);

            main.scheduler().runTaskAsynchronously(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    upsertUser(connection, uuid, level, expStr, highest, now);
                } catch (Exception e) {
                    main.logger("&cFailed to update user " + name + ".");
                    e.printStackTrace();
                }
            });
        }

        @Override
        public void updateUserSync(LevelUser<N> user) {
            if (!isConnected()) return;

            UUID uuid = user.getUuid();
            long now = System.currentTimeMillis();
            String expStr = String.valueOf(user.getExp());
            long level = user.getLevel();
            long highest = getRewardLevel(user);

            try (Connection connection = dataSource.getConnection()) {
                upsertUser(connection, uuid, level, expStr, highest, now);
            } catch (Exception e) {
                main.logger("&cFailed to update user " + user.getName() + " synchronously.");
                e.printStackTrace();
            }
        }

        @Override
        public void removeUser(UUID uuid) {
            if (!isConnected()) return;

            main.scheduler().runTaskAsynchronously(() -> {
                String sql = "DELETE FROM " + qTab(getTable()) + " WHERE " + qCol("UUID") + "=?";
                String metaSql = "DELETE FROM " + qTab(metaTable()) + " WHERE " + qCol("UUID") + "=?";
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement st = connection.prepareStatement(sql);
                     PreparedStatement sm = connection.prepareStatement(metaSql)) {
                    st.setString(1, uuid.toString());
                    st.executeUpdate();

                    sm.setString(1, uuid.toString());
                    sm.executeUpdate();
                } catch (Exception e) {
                    main.logger("&cFailed to remove user " + uuid + " from " + type + " database.");
                    e.printStackTrace();
                }
            });
        }

        @Override
        public LevelUser<N> getUser(Player player) {
            return !isConnected() || player == null ? null : getUser(player.getUniqueId());
        }

        @Override
        public LevelUser<N> getUser(UUID uuid) {
            if (!isConnected() || uuid == null) return null;

            CompletableFuture<LevelUser<N>> future = new CompletableFuture<>();

            main.scheduler().runTaskAsynchronously(() -> {
                future.complete(toLevelUser(fetchUserData(uuid)));
            });

            try {
                return future.get();
            } catch (Exception e) {
                return null;
            }
        }

        @NotNull
        public Set<UUID> getUuids() {
            Set<UUID> uuids = new LinkedHashSet<>();
            if (!isConnected()) return uuids;

            CompletableFuture<Set<UUID>> future = new CompletableFuture<>();

            main.scheduler().runTaskAsynchronously(() -> {
                String sql = "SELECT " + qCol("UUID") + " FROM " + qTab(getTable());

                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet rs = statement.executeQuery())
                {
                    Set<UUID> result = new LinkedHashSet<>();
                    while (rs.next()) {
                        try {
                            result.add(UUID.fromString(rs.getString("UUID")));
                        } catch (Exception ignored) {}
                    }
                    future.complete(result);
                } catch (SQLException e) {
                    main.logger("&cFailed to fetch UUIDs from " + type + ".");
                    e.printStackTrace();
                    future.complete(new LinkedHashSet<>());
                }
            });

            try {
                return future.get();
            } catch (Exception e) {
                return uuids;
            }
        }
    }

    static class MySQL<N extends Number> extends DatabaseImpl<N> {

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
            config.setJdbcUrl("jdbc:mysql://" + ip + ":" + port + "/" + database + "?useSSL=" + ssl + "&autoReconnect=true&useUnicode=true&characterEncoding=utf8");
            config.setUsername(username);
            config.setPassword(password);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(5000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setKeepaliveTime(300000);
            config.setMaximumPoolSize(20);
            config.setMinimumIdle(4);
            config.setPoolName("CLV-MySQL");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("tcpKeepAlive", "true");
            config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            return config;
        }

        @Override
        String qCol(String name) {
            return "`" + name + "`";
        }

        @Override
        String qTab(String name) {
            return "`" + name + "`";
        }

        @Override
        PreparedStatement prepareUpsert(Connection c, UUID uuid, long level, String exp, long updatedAt) throws SQLException {
            String sql =
                    "INSERT INTO " + qTab(getTable()) + " (" +
                            qCol("UUID") + "," + qCol("LEVEL") + "," + qCol("EXP") + "," + qCol("UPDATED_AT") + ") " +
                            "VALUES (?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            qCol("LEVEL") + " = IF(VALUES(" + qCol("UPDATED_AT") + ") >= " + qCol("UPDATED_AT") + ", VALUES(" + qCol("LEVEL") + ")," + qCol("LEVEL") + ")," +
                            qCol("EXP") + " = IF(VALUES(" + qCol("UPDATED_AT") + ") >= " + qCol("UPDATED_AT") + ", VALUES(" + qCol("EXP") + ")," + qCol("EXP") + ")," +
                            qCol("UPDATED_AT") + " = GREATEST(" + qCol("UPDATED_AT") + ", VALUES(" + qCol("UPDATED_AT") + "))";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, uuid.toString());
            ps.setLong(2, level);
            ps.setString(3, exp);
            ps.setLong(4, updatedAt);
            return ps;
        }

        @Override
        PreparedStatement prepareUpsertMeta(Connection c, UUID uuid, long highest, long updatedAt) throws SQLException {
            String sql = "INSERT INTO " + qTab(metaTable()) + " (" +
                    qCol("UUID") + "," + qCol("HIGHEST_REWARDED") + "," + qCol("UPDATED_AT") +
                    ") VALUES (?,?,?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    qCol("HIGHEST_REWARDED") + " = GREATEST(" + qCol("HIGHEST_REWARDED") + ", VALUES(" + qCol("HIGHEST_REWARDED") + "))," +
                    qCol("UPDATED_AT") + " = GREATEST(" + qCol("UPDATED_AT") + ", VALUES(" + qCol("UPDATED_AT") + "))";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, uuid.toString());
            ps.setLong(2, highest);
            ps.setLong(3, updatedAt);
            return ps;
        }

        @Override
        Set<String> getExistingColumns(Connection conn) throws SQLException {
            Set<String> cols = new HashSet<>();
            String sql = "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, getTable());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) cols.add(rs.getString(1).toUpperCase(Locale.ENGLISH));
                }
            }
            return cols;
        }

        @Override
        boolean isExpColumnTextual(Connection conn) throws SQLException {
            String sql = "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'EXP'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, getTable());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String type = rs.getString(1);
                        if (type == null) return false;
                        type = type.toLowerCase(Locale.ENGLISH);
                        return type.contains("text") || type.contains("char");
                    }
                }
            }
            return false;
        }

        @Override
        boolean hasPrimaryKeyOnUuid(Connection conn) throws SQLException {
            String sql = "SELECT k.COLUMN_NAME " +
                    "FROM information_schema.table_constraints t " +
                    "JOIN information_schema.key_column_usage k " +
                    "ON t.constraint_name = k.constraint_name AND t.table_schema = k.table_schema AND t.table_name = k.table_name " +
                    "WHERE t.constraint_type = 'PRIMARY KEY' AND t.table_schema = DATABASE() AND t.table_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, getTable());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String col = rs.getString(1);
                        if ("UUID".equalsIgnoreCase(col)) return true;
                    }
                }
            }
            return false;
        }

        @Override
        void createTargetTable(Connection conn) throws SQLException {
            String sql = "CREATE TABLE IF NOT EXISTS " + qTab(getTable()) + " (" +
                    qCol("UUID") + " VARCHAR(36) NOT NULL," +
                    qCol("LEVEL") + " BIGINT," +
                    qCol("EXP") + " TEXT," +
                    qCol("UPDATED_AT") + " BIGINT NOT NULL DEFAULT 0," +
                    "PRIMARY KEY (" + qCol("UUID") + ")) " +
                    "CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
            }
        }

        @Override
        void dropTableIfExists(Connection conn, String table) throws SQLException {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS " + qTab(table));
            }
        }

        @Override
        void renameTable(Connection conn, String from, String to) throws SQLException {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("RENAME TABLE " + qTab(from) + " TO " + qTab(to));
            }
        }
    }

    static class SQLite<N extends Number> extends DatabaseImpl<N> {

        private final String filePath, table;

        SQLite(CyberLevels main, BaseSystem<N> system) {
            super(main, system, "SQLite");
            Config.Database db = main.cache().config().database();
            this.filePath = db.getSqliteFile();
            this.table = db.getTable();
        }

        @Override String getTable() { return table; }

        @Override
        HikariConfig createConfig() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + filePath + "?busy_timeout=5000&journal_mode=WAL&synchronous=NORMAL");
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(5000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setKeepaliveTime(300000);
            config.setPoolName("CLV-SQLite");
            return config;
        }

        @Override
        String qCol(String name) {
            return "\"" + name + "\"";
        }

        @Override
        String qTab(String name) {
            return "\"" + name + "\"";
        }

        @Override
        PreparedStatement prepareUpsert(Connection c, UUID uuid, long level, String exp, long updatedAt) throws SQLException {
            String sql =
                    "INSERT INTO " + qTab(getTable()) + " (" +
                            qCol("UUID") + "," + qCol("LEVEL") + "," + qCol("EXP") + "," + qCol("UPDATED_AT") + ") " +
                            "VALUES (?,?,?,?) " +
                            "ON CONFLICT(" + qCol("UUID") + ") DO UPDATE SET " +
                            qCol("LEVEL") + " = CASE WHEN excluded." + qCol("UPDATED_AT") + " >= " + qTab(getTable()) + "." + qCol("UPDATED_AT") + " THEN excluded." + qCol("LEVEL") + " ELSE " + qTab(getTable()) + "." + qCol("LEVEL") + " END," +
                            qCol("EXP") + " = CASE WHEN excluded." + qCol("UPDATED_AT") + " >= " + qTab(getTable()) + "." + qCol("UPDATED_AT") + " THEN excluded." + qCol("EXP") + " ELSE " + qTab(getTable()) + "." + qCol("EXP") + " END," +
                            qCol("UPDATED_AT") + " = MAX(" + qTab(getTable()) + "." + qCol("UPDATED_AT") + ", excluded." + qCol("UPDATED_AT") + ")";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, uuid.toString());
            ps.setLong(2, level);
            ps.setString(3, exp);
            ps.setLong(4, updatedAt);
            return ps;
        }

        @Override
        PreparedStatement prepareUpsertMeta(Connection c, UUID uuid, long highest, long updatedAt) throws SQLException {
            String sql = "INSERT INTO " + qTab(metaTable()) + " (" +
                    qCol("UUID") + "," + qCol("HIGHEST_REWARDED") + "," + qCol("UPDATED_AT") +
                    ") VALUES (?,?,?) " +
                    "ON CONFLICT(" + qCol("UUID") + ") DO UPDATE SET " +
                    qCol("HIGHEST_REWARDED") + " = MAX(" + qTab(metaTable()) + "." + qCol("HIGHEST_REWARDED") + ", excluded." + qCol("HIGHEST_REWARDED") + ")," +
                    qCol("UPDATED_AT") + " = MAX(" + qTab(metaTable()) + "." + qCol("UPDATED_AT") + ", excluded." + qCol("UPDATED_AT") + ")";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, uuid.toString());
            ps.setLong(2, highest);
            ps.setLong(3, updatedAt);
            return ps;
        }

        @Override
        Set<String> getExistingColumns(Connection conn) throws SQLException {
            Set<String> cols = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + getTable() + ")");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(rs.getString("name").toUpperCase(Locale.ENGLISH));
                }
            }
            return cols;
        }

        @Override
        boolean isExpColumnTextual(Connection conn) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + getTable() + ")");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (!"EXP".equalsIgnoreCase(name)) continue;
                    String type = rs.getString("type"); // e.g. TEXT, NUMERIC, REAL
                    if (type == null) return false;
                    type = type.toUpperCase(Locale.ENGLISH);
                    return type.contains("TEXT") || type.contains("CHAR");
                }
            }
            return false;
        }

        @Override
        boolean hasPrimaryKeyOnUuid(Connection conn) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + getTable() + ")");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    int pk = rs.getInt("pk");
                    if ("UUID".equalsIgnoreCase(name) && pk == 1) return true;
                }
            }
            return false;
        }

        @Override
        void createTargetTable(Connection conn) throws SQLException {
            String sql = "CREATE TABLE IF NOT EXISTS " + qTab(getTable()) + " (" +
                    qCol("UUID") + " TEXT PRIMARY KEY," +
                    qCol("LEVEL") + " INTEGER," +
                    qCol("EXP") + " TEXT," +
                    qCol("UPDATED_AT") + " INTEGER NOT NULL DEFAULT 0" +
                    ")";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
            }
        }

        @Override
        void dropTableIfExists(Connection conn, String table) throws SQLException {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS " + qTab(table));
            }
        }

        @Override
        void renameTable(Connection conn, String from, String to) throws SQLException {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE " + qTab(from) + " RENAME TO " + qTab(to));
            }
        }
    }

    static class PostgreSQL<N extends Number> extends DatabaseImpl<N> {

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

        @Override String getTable() {
            return table;
        }

        @Override
        HikariConfig createConfig() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://" + ip + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(5000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setKeepaliveTime(300000);
            config.setMaximumPoolSize(20);
            config.setMinimumIdle(4);
            config.setPoolName("CLV-Postgres");
            config.addDataSourceProperty("tcpKeepAlive", "true");
            return config;
        }

        @Override
        String qCol(String name) {
            return "\"" + name + "\"";
        }

        @Override
        String qTab(String name) {
            return "\"" + name + "\"";
        }

        @Override
        PreparedStatement prepareUpsert(Connection c, UUID uuid, long level, String exp, long updatedAt) throws SQLException {
            String sql =
                    "INSERT INTO " + qTab(getTable()) + " (" +
                            qCol("UUID") + "," + qCol("LEVEL") + "," + qCol("EXP") + "," + qCol("UPDATED_AT") + ") " +
                            "VALUES (?,?,?,?) " +
                            "ON CONFLICT (" + qCol("UUID") + ") DO UPDATE SET " +
                            qCol("LEVEL") + " = CASE WHEN EXCLUDED." + qCol("UPDATED_AT") + " >= " + qTab(getTable()) + "." + qCol("UPDATED_AT") + " THEN EXCLUDED." + qCol("LEVEL") + " ELSE " + qTab(getTable()) + "." + qCol("LEVEL") + " END," +
                            qCol("EXP") + " = CASE WHEN EXCLUDED." + qCol("UPDATED_AT") + " >= " + qTab(getTable()) + "." + qCol("UPDATED_AT") + " THEN EXCLUDED." + qCol("EXP") + " ELSE " + qTab(getTable()) + "." + qCol("EXP") + " END," +
                            qCol("UPDATED_AT") + " = GREATEST(" + qTab(getTable()) + "." + qCol("UPDATED_AT") + ", EXCLUDED." + qCol("UPDATED_AT") + ")";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, uuid.toString());
            ps.setLong(2, level);
            ps.setString(3, exp);
            ps.setLong(4, updatedAt);
            return ps;
        }

        @Override
        PreparedStatement prepareUpsertMeta(Connection c, UUID uuid, long highest, long updatedAt) throws SQLException {
            String sql =
                    "INSERT INTO " + qTab(metaTable()) + " (" +
                            qCol("UUID") + "," + qCol("HIGHEST_REWARDED") + "," + qCol("UPDATED_AT") + ") " +
                            "VALUES (?,?,?) " +
                            "ON CONFLICT (" + qCol("UUID") + ") DO UPDATE SET " +
                            qCol("HIGHEST_REWARDED") + " = GREATEST(" + qTab(metaTable()) + "." + qCol("HIGHEST_REWARDED") + ", EXCLUDED." + qCol("HIGHEST_REWARDED") + ")," +
                            qCol("UPDATED_AT") + " = GREATEST(" + qTab(metaTable()) + "." + qCol("UPDATED_AT") + ", EXCLUDED." + qCol("UPDATED_AT") + ")";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, uuid.toString());
            ps.setLong(2, highest);
            ps.setLong(3, updatedAt);
            return ps;
        }

        @Override
        Set<String> getExistingColumns(Connection conn) throws SQLException {
            Set<String> cols = new HashSet<>();
            String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, getTable());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) cols.add(rs.getString(1).toUpperCase(Locale.ENGLISH));
                }
            }
            return cols;
        }

        @Override
        boolean isExpColumnTextual(Connection conn) throws SQLException {
            String sql = "SELECT data_type FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND column_name = 'exp'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, getTable());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String type = rs.getString(1);
                        if (type == null) return false;
                        type = type.toLowerCase(Locale.ENGLISH);
                        return type.contains("text") || type.contains("char");
                    }
                }
            }
            return false;
        }

        @Override
        boolean hasPrimaryKeyOnUuid(Connection conn) throws SQLException {
            String sql =
                    "SELECT a.attname " +
                            "FROM pg_index i " +
                            "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) " +
                            "WHERE i.indrelid = ?::regclass AND i.indisprimary";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, getTable());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String col = rs.getString(1);
                        if ("UUID".equalsIgnoreCase(col)) return true;
                    }
                }
            }
            return false;
        }

        @Override
        void createTargetTable(Connection conn) throws SQLException {
            String sql = "CREATE TABLE IF NOT EXISTS " + qTab(getTable()) + " (" +
                    qCol("UUID") + " VARCHAR(36) PRIMARY KEY," +
                    qCol("LEVEL") + " BIGINT," +
                    qCol("EXP") + " TEXT," +
                    qCol("UPDATED_AT") + " BIGINT NOT NULL DEFAULT 0" +
                    ")";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
            }
        }

        @Override
        void dropTableIfExists(Connection conn, String table) throws SQLException {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS " + qTab(table));
            }
        }

        @Override
        void renameTable(Connection conn, String from, String to) throws SQLException {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE " + qTab(from) + " RENAME TO " + qTab(to));
            }
        }
    }

    static <N extends Number> Database<N> createDatabase(CyberLevels main, BaseSystem<N> system) {
        String type = main.cache().config().database().getType();

        switch (type.toUpperCase(Locale.ENGLISH)) {
            case "POSTGRES":
            case "POSTGRESQL":
                return new PostgreSQL<>(main, system);
            case "MYSQL":
            case "MARIADB":
                return new MySQL<>(main, system);
            case "SQLITE":
            default:
                return new SQLite<>(main, system);
        }
    }
}
