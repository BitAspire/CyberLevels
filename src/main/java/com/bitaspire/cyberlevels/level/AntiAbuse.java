package com.bitaspire.cyberlevels.level;

import com.bitaspire.cyberlevels.CyberLevels;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;

/**
 * Public view of one anti-abuse module configured in CyberLevels.
 *
 * <p>An anti-abuse module can limit how frequently a player is rewarded, cap how many rewards they
 * can receive within a time window, and restrict operation to specific worlds. Implementations are
 * expected to be stateful because they track per-player counters and cooldowns across gameplay.
 */
public interface AntiAbuse {

    /**
     * Indicates whether cooldown enforcement is enabled for this module.
     *
     * @return {@code true} when cooldown checks should run
     */
    boolean isCooldownEnabled();

    /**
     * Returns the configured cooldown duration in seconds.
     *
     * @return cooldown length as configured by the module
     */
    int getCooldownTime();

    /**
     * Returns the remaining cooldown for a player.
     *
     * @param player player whose cooldown should be checked
     * @return remaining cooldown in seconds, or {@code 0} when none is active
     */
    int getCooldownLeft(Player player);

    /**
     * Clears every tracked cooldown entry in this module.
     */
    void resetCooldowns();

    /**
     * Clears the tracked cooldown for a specific player.
     *
     * @param player player whose cooldown should be removed
     */
    void resetCooldown(Player player);

    /**
     * Indicates whether limiter enforcement is enabled for this module.
     *
     * @return {@code true} when limiter checks should run
     */
    boolean isLimiterEnabled();

    /**
     * Returns the configured limiter budget.
     *
     * @return maximum number of allowed reward operations before the limiter blocks them
     */
    long getLimiterAmount();

    /**
     * Returns the remaining limiter budget for a player.
     *
     * @param player player whose limiter state should be checked
     * @return remaining allowed operations before the limiter triggers
     */
    int getLimiter(Player player);

    /**
     * Clears the limiter state for every tracked player.
     */
    void resetLimiters();

    /**
     * Clears the limiter state for a specific player.
     *
     * @param player player whose limiter should be removed
     */
    void resetLimiter(Player player);

    /**
     * Returns the scheduled reset timer associated with this module.
     *
     * @return timer controlling automatic limiter resets, or {@code null} when none is configured
     */
    Timer getTimer();

    /**
     * Stops and purges the configured reset timer, if any.
     */
    void cancelTimer();

    /**
     * Indicates whether this module restricts itself to a configured world list.
     *
     * @return {@code true} when world filtering is enabled
     */
    boolean isWorldsEnabled();

    /**
     * Indicates how the configured world list should be interpreted.
     *
     * @return {@code true} when the world list acts as a whitelist, otherwise a blacklist
     */
    boolean isWorldsWhitelist();

    /**
     * Evaluates whether this module should block a reward attempt.
     *
     * @param player player attempting to receive EXP
     * @param source EXP source being processed
     * @return {@code true} when the action should be denied by this module
     */
    boolean isLimited(Player player, ExpSource source);

    /**
     * Scheduler helper used by anti-abuse modules that need periodic limiter resets.
     *
     * <p>The timer accepts the date/time expression format used in the plugin configuration, parses
     * the first execution time plus any repeat intervals, and then re-schedules itself after each
     * completed reset.
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
         * Absolute epoch time, in milliseconds, when the next limiter reset is expected to happen.
         */
        @Getter
        private long resetEpochTime = Long.MAX_VALUE;

        /**
         * Creates a timer from the raw anti-abuse schedule string.
         *
         * @param main owning plugin instance used for scheduling callbacks
         * @param antiAbuse anti-abuse module whose limiters should be reset
         * @param unformatted raw schedule expression from the configuration
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
         * Starts the scheduler asynchronously.
         *
         * <p>The actual parsing and scheduling work is performed off the main thread, while the
         * limiter reset callback itself is marshalled back to the scheduler supplied by the plugin.
         */
        public void start() {
            main.scheduler().runTaskAsynchronously(() -> startScheduler(false));
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
         * Cancels the currently scheduled timer task and purges the underlying timer queue.
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

                main.scheduler().runTask(antiAbuse::resetLimiters);
                main.scheduler().runTaskLaterAsynchronously(() -> startScheduler(true), 20L);
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
