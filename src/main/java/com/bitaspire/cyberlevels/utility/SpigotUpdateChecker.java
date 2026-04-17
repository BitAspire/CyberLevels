package com.bitaspire.cyberlevels.utility;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.cache.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Compares the running plugin version with the SpigotMC resource listing (legacy API).
 * Runs network I/O asynchronously and posts results on the main thread.
 */
public final class SpigotUpdateChecker {

    private static final int RESOURCE_ID = 98826;
    private static final String UPDATE_URL = "https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID;
    private static final String RESOURCE_PAGE_URL = "https://www.spigotmc.org/resources/" + RESOURCE_ID + "/";
    private static final String DISCORD_URL = "https://discord.gg/DC4Gqj3y5V";
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 8_000;

    private SpigotUpdateChecker() {
    }

    /**
     * Schedules an async fetch of the Spigot-listed version; logs a warning if the listing is newer,
     * or an info line if the local JAR is ahead (early access). No-op when versions match or on failure.
     *
     * @param main plugin instance used for scheduling, version metadata, and config
     */
    public static void checkAsync(CyberLevels main) {
        main.scheduler().runTaskAsynchronously(() -> {
            final String remote;
            try {
                remote = fetchLatestVersion(main);
            } catch (Exception ignored) {
                return;
            }
            if (remote == null || remote.isEmpty()) return;

            final String local = main.getDescription().getVersion();
            final int cmp = compareVersions(remote, local);
            if (cmp == 0) {
                main.scheduler().runTask(() -> main.setSpigotOpUpdateNotice(CyberLevels.SpigotOpUpdateNotice.none()));
                return;
            }

            if (cmp > 0) {
                main.scheduler().runTask(() -> {
                    if (!main.isEnabled()) return;
                    main.setSpigotOpUpdateNotice(CyberLevels.SpigotOpUpdateNotice.newer(remote, local));
                    final String console = "A newer version is available on Spigot: " + remote
                            + " (you are running " + local + "). Download: " + RESOURCE_PAGE_URL;
                    main.getLogger().warning(console);
                    if (main.cache().config().spigotUpdateCheckNotifyOpsChat()) messageOpsLang(main);
                });
                return;
            }

            main.scheduler().runTask(() -> {
                if (!main.isEnabled()) return;
                main.setSpigotOpUpdateNotice(CyberLevels.SpigotOpUpdateNotice.earlyAccess(local));
                final String console = "You are running an early access build (" + local
                        + "). If you encounter any issues, please report them on our Discord: "
                        + DISCORD_URL;
                main.getLogger().info(console);
                if (main.cache().config().spigotUpdateCheckNotifyOpsChat()) messageOpsLang(main);
            });
        });
    }

    /**
     * Sends the cached Spigot update notice to one operator after join (if enabled in config).
     *
     * @param main   plugin
     * @param player joining player
     */
    public static void deliverPendingOpChatOnJoin(CyberLevels main, Player player) {
        if (!player.isOp()) return;
        if (!main.cache().config().spigotUpdateCheckNotifyOpsChat()) return;
        sendOpLangNotice(main, player, main.getSpigotOpUpdateNotice());
    }

    private static void messageOpsLang(CyberLevels main) {
        final CyberLevels.SpigotOpUpdateNotice notice = main.getSpigotOpUpdateNotice();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) sendOpLangNotice(main, player, notice);
        }
    }

    private static void sendOpLangNotice(CyberLevels main, Player player, CyberLevels.SpigotOpUpdateNotice notice) {
        if (notice.getKind() == CyberLevels.SpigotOpUpdateNotice.KIND_NONE) return;
        if (notice.getKind() == CyberLevels.SpigotOpUpdateNotice.KIND_NEWER) {
            main.cache().lang().sendMessage(
                    player,
                    Lang::getSpigotUpdateNewerChat,
                    new String[]{"remoteVersion", "localVersion", "resourceUrl"},
                    notice.getRemoteVersion(),
                    notice.getLocalVersion(),
                    RESOURCE_PAGE_URL);
            return;
        }
        if (notice.getKind() == CyberLevels.SpigotOpUpdateNotice.KIND_EARLY) {
            main.cache().lang().sendMessage(
                    player,
                    Lang::getSpigotUpdateEarlyAccessChat,
                    new String[]{"localVersion", "discordUrl"},
                    notice.getLocalVersion(),
                    DISCORD_URL);
        }
    }

    private static String fetchLatestVersion(JavaPlugin plugin) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", plugin.getName() + "/" + plugin.getDescription().getVersion());

        final int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) return null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            final String line = reader.readLine();
            return line != null ? line.trim() : null;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Semver-style comparison on the numeric core (strip {@code -prerelease} suffix from the full string).
     * Each dot segment uses its leading digits; missing segments count as 0.
     *
     * @param a first version (e.g. from Spigot API)
     * @param b second version (e.g. plugin.yml)
     * @return positive if {@code a} &gt; {@code b}, negative if {@code a} &lt; {@code b}, else 0
     */
    private static int compareVersions(String a, String b) {
        final String[] pa = normalizeVersionCore(a).split("\\.");
        final String[] pb = normalizeVersionCore(b).split("\\.");
        final int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            final int na = parseNumericSegment(i < pa.length ? pa[i] : "0");
            final int nb = parseNumericSegment(i < pb.length ? pb[i] : "0");
            if (na != nb) return Integer.compare(na, nb);
        }
        return 0;
    }

    private static String normalizeVersionCore(String version) {
        if (version == null) return "0";
        final int dash = version.indexOf('-');
        return dash >= 0 ? version.substring(0, dash) : version;
    }

    private static int parseNumericSegment(String segment) {
        if (segment == null || segment.isEmpty()) return 0;
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(segment.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
