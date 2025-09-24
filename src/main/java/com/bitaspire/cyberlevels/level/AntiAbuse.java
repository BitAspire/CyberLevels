package com.bitaspire.cyberlevels.level;

import com.bitaspire.cyberlevels.CyberLevels;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;

/**
 * Represents an anti-abuse mechanism for managing cooldowns and limiters in a leveling system.
 *
 * <p> This interface provides methods to check and manage cooldowns, limiters, and world restrictions
 * for players based on experience sources.
 */
public interface AntiAbuse {

    /**
     * Checks if the cooldown feature is enabled.
     * @return true if cooldown is enabled, false otherwise
     */
    boolean isCooldownEnabled();

    /**
     * Gets the cooldown time in milliseconds.
     * @return the cooldown time
     */
    int getCooldownTime();

    /**
     * Gets the remaining cooldown time for a player in milliseconds.
     *
     * @param player the player to check
     * @return the remaining cooldown time, or 0 if no cooldown is active
     */
    int getCooldownLeft(Player player);

    /**
     * Resets all cooldowns for all players.
     */
    void resetCooldowns();

    /**
     * Resets the cooldown for a specific player.
     * @param player the player whose cooldown is to be reset
     */
    void resetCooldown(Player player);

    /**
     * Checks if the limiter feature is enabled.
     * @return true if limiter is enabled, false otherwise
     */
    boolean isLimiterEnabled();

    /**
     * Gets the maximum amount allowed by the limiter.
     * @return the limiter amount
     */
    long getLimiterAmount();

    /**
     * Gets the current amount used by the limiter for a specific player.
     *
     * @param player the player to check
     * @return the current amount used by the limiter
     */
    int getLimiter(Player player);

    /**
     * Resets all limiters for all players.
     */
    void resetLimiters();

    /**
     * Resets the limiter for a specific player.
     * @param player the player whose limiter is to be reset
     */
    void resetLimiter(Player player);

    /**
     * Gets the timer associated with this anti-abuse mechanism.
     * @return the Timer instance
     */
    Timer getTimer();

    /**
     * Cancels the timer and purges any scheduled tasks.
     */
    void cancelTimer();

    /**
     * Checks if world restrictions are enabled.
     * @return true if world restrictions are enabled, false otherwise
     */
    boolean isWorldsEnabled();

    /**
     * Checks if the world list is a whitelist.
     * @return true if the world list is a whitelist, false if it's a blacklist
     */
    boolean isWorldsWhitelist();

    /**
     * Checks if a player is limited in a specific experience source.
     *
     * @param player the player to check
     * @param source the experience source
     *
     * @return true if the player is limited, false otherwise
     */
    boolean isLimited(Player player, ExpSource source);

    /**
     * Timer class to handle scheduled resets for anti-abuse limiters.
     *
     * <p> This class parses a formatted string to determine reset intervals and schedules tasks accordingly.
     */
    class Timer {

        private final CyberLevels main;
        private final AntiAbuse antiAbuse;
        private final String unformatted;

        private java.util.Timer timer;
        private String[] date;
        private String[] time;
        private String[] intervals;

        /**
         * The next reset epoch time in milliseconds.
         * */
        @Getter
        private long resetEpochTime = Long.MAX_VALUE;

        /**
         * Constructs a Timer instance with the specified parameters.
         *
         * @param main       the main CyberLevels instance
         * @param antiAbuse  the AntiAbuse instance associated with this timer
         * @param unformatted the unformatted string representing the reset schedule
         */
        public Timer(CyberLevels main, AntiAbuse antiAbuse, String unformatted) {
            this.main = main;
            this.antiAbuse = antiAbuse;
            this.unformatted = unformatted;

            try {
                parseUnformatted();
            } catch (Exception e) {
                if (unformatted.equalsIgnoreCase("yyyy-MM-dd HH:mm")) return;
                main.logger("&cSomething went wrong parsing the reset timer " + unformatted);
            }
        }

        /**
         * Starts the timer asynchronously.
         */
        public void start() {
            new BukkitRunnable() {
                public void run() {
                    startScheduler(false);
                }
            }.runTaskAsynchronously(main);
        }

        private void startScheduler(boolean cancelTimer) {
            if (cancelTimer) purge();
            timer = new java.util.Timer();

            try {
                long time = parseNextScheduler();
                if (time <= 0) {
                    resetEpochTime = Long.MAX_VALUE;
                    return;
                }

                resetEpochTime = (System.currentTimeMillis() / 1000L) * 1000L + time;
                run(time);

            } catch (final ParseException e) {
                main.logger("&cSomething went wrong parsing the next reset time for " + unformatted);
                e.printStackTrace();
            }
        }

        private void run(long intervalMS) {
            timer.schedule(new TimedTask(), intervalMS);
        }

        /**
         * Cancels the timer and purges any scheduled tasks.
         */
        public void purge() {
            timer.cancel();
            timer.purge();
        }

        private void parseUnformatted() {
            date = time = null;
            Set<String> intervals = new HashSet<>();

            String[] split = unformatted.replace("  ", " ").split(" ");

            for (String s : split) {
                if (s.equalsIgnoreCase("")) continue;

                if (s.contains("-")) {
                    String[] dateSplitter = s.split("-");
                    String year = LocalDate.now().getYear() + "";

                    if (dateSplitter.length == 3) year = dateSplitter[0];

                    date = new String[] {
                            year,
                            dateSplitter[dateSplitter.length - 2],
                            dateSplitter[dateSplitter.length - 1]
                    };
                    continue;
                }

                if (s.contains(":")) {
                    String[] timeSplitter = s.split(":");

                    String hour = timeSplitter[0];
                    String minute = timeSplitter[1].toUpperCase(Locale.ENGLISH);

                    if (hour.startsWith("12") && minute.endsWith("AM")) hour = "00";
                    minute = minute.replace("AM", "");
                    if (minute.contains("PM")) {
                        minute = minute.replace("PM", "");
                        int h = Integer.parseInt(hour);
                        hour = (h != 12 ? h + 12 : h) + "";
                    }

                    time = new String[] {hour, minute};
                    continue;
                }

                StringBuilder comp = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (Character.isDigit(c)) {
                        comp.append(c);
                        continue;
                    }

                    intervals.add(comp.toString() + c);
                    comp = new StringBuilder();
                }
            }

            if (date == null) {
                int currentYear = yearNow();
                int currentMonth = monthNow();
                int currentDay = dayNow();
                int monthLength = YearMonth.of(currentYear, currentMonth).lengthOfMonth();

                if (currentDay > monthLength) {
                    currentMonth += 1;
                    if (currentMonth > 12) {
                        currentMonth = 1;
                        currentYear += 1;
                    }
                    currentDay = 1;
                }

                date = new String[] {currentYear + "", currentMonth + "", currentDay + ""};
                if (intervals.isEmpty()) intervals.add("1d");
            }

            if (time == null) time = new String[] {"00", "00"};

            if (date.length == 3 && date[0].equals("****")) date[0] = yearNow() + "";
            if (date[date.length - 2].equals("**")) date[date.length - 2] = monthNow() + "";
            if (date[date.length - 1].equals("**")) date[date.length - 1] = dayNow() + "";

            if (time[0].equals("**")) time[0] = hourNow() + "";
            if (time[1].equals("**")) time[1] = minuteNow() + "";

            this.intervals = intervals.toArray(new String[0]);
        }

        private long parseNextScheduler() throws ParseException {
            if (time.length == 2) time = new String[] {time[0], time[1], "00"};

            SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

            String startString = yearNow() + "/" + monthNow() + "/" + dayNow() + " " + hourNow() + ":" + minuteNow() + ":" + secondNow();
            String endString = date[0] + "/" + date[1] + "/" + date[2] + " " + time[0] + ":" + time[1] + ":" + time[2];

            final Date startDate = format.parse(startString);
            final Date endDate = format.parse(endString);

            long difference = endDate.getTime() - startDate.getTime();
            if (difference > 0) return difference;

            if (intervals.length == 0) return -1;

            long timeInterval = difference;

            while (timeInterval <= 0) {
                for (String s : intervals) {
                    int singleTimeInterval = Integer.parseInt(s.replaceAll("[^0-9]", ""));
                    char timeIntervalID = s.charAt((singleTimeInterval + "").length());

                    switch (timeIntervalID) {
                        case 's':
                            timeInterval += singleTimeInterval * 1000L;
                            break;
                        case 'm':
                            timeInterval += singleTimeInterval * 60000L;
                            break;
                        case 'h':
                            timeInterval += singleTimeInterval * 3600000L;
                            break;
                        case 'd':
                            timeInterval += singleTimeInterval * 86400000L;
                            break;
                        case 'w':
                            timeInterval += singleTimeInterval * 604800000L;
                            break;
                        case 'M':
                            LocalDateTime ldt = LocalDateTime.of(
                                    Integer.parseInt(date[0]),
                                    Integer.parseInt(date[1]),
                                    Integer.parseInt(date[2]),
                                    Integer.parseInt(time[0]),
                                    Integer.parseInt(time[1]),
                                    Integer.parseInt(time[2])
                            ).plusMonths(singleTimeInterval);

                            timeInterval += ldt.toEpochSecond(ZoneId.systemDefault().getRules().getOffset(Instant.now())) * 1000 - endDate.getTime();
                            break;
                    }
                }

                if (timeInterval <= 0) {
                    LocalDateTime ltd = LocalDateTime.ofEpochSecond(
                            (endDate.getTime() + (timeInterval - difference)) / 1000,
                            0,
                            ZoneId.systemDefault().getRules().getOffset(Instant.now())
                    );

                    date = new String[] {ltd.getYear() + "", ltd.getMonth().getValue() + "", ltd.getDayOfMonth() + ""};
                    time = new String[] {ltd.getHour() + "", ltd.getMinute() + "", ltd.getSecond() + ""};
                }
            }

            return timeInterval;
        }

        private class TimedTask extends TimerTask {
            @Override
            public void run() {
                if (main == null || !main.isEnabled()) return;

                Bukkit.getScheduler().runTask(main, antiAbuse::resetLimiters);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        startScheduler(true);
                    }
                }.runTaskLaterAsynchronously(main, 20L);
            }
        }

        // Helpers
        static int yearNow() { return LocalDateTime.now().getYear(); }
        static int monthNow() { return LocalDateTime.now().getMonth().getValue(); }
        static int dayNow() { return LocalDateTime.now().getDayOfMonth(); }
        static int hourNow() { return LocalDateTime.now().getHour(); }
        static int minuteNow() { return LocalDateTime.now().getMinute(); }
        static int secondNow() { return LocalDateTime.now().getSecond(); }
    }
}
