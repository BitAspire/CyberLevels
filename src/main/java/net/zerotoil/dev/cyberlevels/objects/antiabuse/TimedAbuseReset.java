package net.zerotoil.dev.cyberlevels.objects.antiabuse;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.zerotoil.dev.cyberlevels.CyberLevels;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class TimedAbuseReset {

    final CyberLevels main;
    final AntiAbuse antiAbuse;

    String unformatted;
    String[] dateTimeInterval;
    Timer timer = new Timer();

    String[] date;
    String[] time;
    String unformattedInterval = null;
    long intervalSeconds;

    long resetTime;

    public TimedAbuseReset(CyberLevels main, AntiAbuse antiAbuse, String time) {

        this.main = main;
        this.antiAbuse = antiAbuse;
        unformatted = time;
        try {
            formatTime(false);
        } catch (Exception e) {
            if (time.equalsIgnoreCase("yyyy-MM-dd HH:mm")) return;
            main.logger("&cSomething went wrong parsing the timer " + time);
        }

    }

    public void formatTime(boolean cancelTimer) throws ParseException {

        if (cancelTimer) cancelTimer();
        timer = new Timer();

        dateTimeInterval = unformatted.split(" ");

        date = dateTimeInterval[0].split("-");
        time = dateTimeInterval[1].split(":");

        Calendar cal = Calendar.getInstance();

        String yearNow = new SimpleDateFormat("yyyy").format(cal.getTime());
        String monthNow = new SimpleDateFormat("MM").format(cal.getTime());
        String dayNow = new SimpleDateFormat("dd").format(cal.getTime());

        String hourNow = new SimpleDateFormat("HH").format(cal.getTime());
        String minuteNow = new SimpleDateFormat("mm").format(cal.getTime());
        String secondNow = new SimpleDateFormat("ss").format(cal.getTime());

        //System.out.println(yearNow + " " + monthNow + " " + dayNow + " " + hourNow + ":" + minuteNow);
        int secondNowInt = Integer.parseInt(secondNow);

        // if using current time
        if (date[0].equals("****")) date[0] = yearNow;
        if (date[1].equals("**")) date[1] = monthNow;
        if (date[2].equals("**")) date[2] = dayNow;

        if (time[0].equals("**")) time[0] = hourNow;
        if (time[1].equals("**")) time[1] = minuteNow;

        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date startDate = format.parse(yearNow + "/" + monthNow + "/" + dayNow + " " + hourNow + ":" + minuteNow + ":" + secondNow);
        Date endDate = format.parse(date[0] + "/" + date[1] + "/" + date[2] + " " + time[0] + ":" + time[1] + ":00");
        long difference = endDate.getTime() - startDate.getTime();

        if ((dateTimeInterval.length == 3) && (endDate.getTime() < startDate.getTime())) {
            unformattedInterval = dateTimeInterval[2];

            int preTimeInterval = Integer.parseInt(unformattedInterval.replaceAll("[^0-9]", ""));
            char intervalFormatter = unformattedInterval.charAt((preTimeInterval + "").length());

            switch (intervalFormatter) {
                case 'm':

                    intervalSeconds = (preTimeInterval * 60L) - Math.abs(Math.round(difference / 1000.0) % (preTimeInterval * 60L));
                    break;
                case 'h':
                    intervalSeconds = (preTimeInterval * 3600L) - Math.abs(Math.round(difference / 1000.0) % (preTimeInterval * 3600L));
                    break;
                case 'd':
                    intervalSeconds = (preTimeInterval * 86400L) - Math.abs(Math.round(difference / 1000.0) % (preTimeInterval * 86400L));
                    break;
                case 'M':
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy MM dd ss");

                    // current time
                    // interval


                    String endDay = date[2];
                    if (Month.of(checkZero((Integer.parseInt(date[1]) + preTimeInterval) % 12)).length(false) < Integer.parseInt(date[2])) {

                        endDay = Month.of(checkZero((Integer.parseInt(date[1]) + preTimeInterval) % 12)).length(false) + "";

                    }

                    String endDateString = date[0] + " " + doubleDigits(toShort(date[1]) + preTimeInterval) + " " + date[2];
                    if ((preTimeInterval + toShort(date[1])) > 12) {

                        endDateString = (toShort(date[0]) + (preTimeInterval + toShort(date[1]) - checkZero((preTimeInterval + toShort(date[1])) % 12)) / 12) + " " +
                                doubleDigits(checkZero((toShort(date[1]) + preTimeInterval) % 12)) + " " + endDay;

                    }

                    LocalDate startDate2 = LocalDate.parse(date[0] + " " + date[1] + " " + date[2] + " " + secondNow, dtf);
                    LocalDate endDate2 = LocalDate.parse(endDateString + " 00", dtf);
                    intervalSeconds = Duration.between(startDate2.atStartOfDay(), endDate2.atStartOfDay()).toDays() * 86400;
                    //System.out.println(intervalSeconds);
                    break;

                default:
                    break;
            }

        } else {

            intervalSeconds = difference / 1000;

        }

        //System.out.println(time[0]);

        if (intervalSeconds <= 0) return;

        resetTime = (intervalSeconds * 1000) + System.currentTimeMillis();
        run(intervalSeconds);

    }

    public void run(long intervalSeconds) {
        timer.schedule(new MyTimeTask(), intervalSeconds * 1000);
    }


    private class MyTimeTask extends TimerTask {

        public void run() {

            try {
                // regen world
                Bukkit.getScheduler().runTask(main, antiAbuse::resetLimiters);

                // reruns timed reset
                (new BukkitRunnable() {

                    @Override
                    public void run() {
                        try {
                            formatTime(true);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }

                }).runTaskLater(main, 200L);
            } catch (Exception e) {
                // nothing
            }

        }

    }

    protected void cancelTimer() {
        timer.cancel();
        timer.purge();
    }

    private short toShort(String string) {
        return Short.parseShort(string);
    }

    private String doubleDigits(long number) {

        if (number >= 10) return number + "";
        return "0" + number;

    }

    private int checkZero(int number) {

        if (number == 0) return 12;
        return number;

    }

    public long timeToReset() {
        return (long) (resetTime / 1000 - (Math.floor(System.currentTimeMillis() / 1000.0)));
    }

    protected Timer getTimer() {
        return timer;
    }



}
