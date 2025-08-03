package net.zerotoil.dev.cyberlevels.utilities;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.clip.placeholderapi.PlaceholderAPI;
import me.croabeast.beanslib.Beans;
import net.zerotoil.dev.cyberlevels.CyberLevels;
import net.zerotoil.dev.cyberlevels.objects.ActionBar;
import net.zerotoil.dev.cyberlevels.objects.Title;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class LangUtils {

    final CyberLevels main;
    final ActionBar actionBar;
    final Title title;
    final String prefix;

    public LangUtils(CyberLevels main) {
        this.main = main;
        actionBar = new ActionBar(main);
        title = new Title(main);
        prefix = main.getConfig("lang").getString("messages.prefix");
    }

    public String parsePAPI(Player player, String message) {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ?
                PlaceholderAPI.setPlaceholders(player, message) : message;
    }

    // converts message to list
    public List<String> convertList(ConfigurationSection config, String path) {
        if (config == null) return new ArrayList<>();
        return  !config.isList(path) ?
                Lists.newArrayList(config.getString(path)) :
                config.getStringList(path);
    }

    public String colorize(Player player, String message) {
        return Beans.colorize(parsePAPI(player, message));
    }

    public void sendCentered(Player player, String message) {
        message = colorize(player, message);

        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        for (char c : message.toCharArray()) {
            if (c == '§') previousCode = true;
            else if (previousCode) {
                previousCode = false;
                isBold = c == 'l' || c == 'L';
            } else {
                FontInfo dFI = FontInfo.getDefaultFontInfo(c);
                messagePxSize += isBold ?
                        dFI.getBoldLength() : dFI.getLength();
                messagePxSize++;
            }
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = 154 - halvedMessageSize;
        int spaceLength = FontInfo.SPACE.getLength() + 1;
        int compensated = 0;

        StringBuilder sb = new StringBuilder();
        while (compensated < toCompensate) {
            sb.append(" ");
            compensated += spaceLength;
        }

        player.sendMessage(sb + message);
    }

    public void sendMixed(Player player, String message) {
        if (player == null) {
            if (message == null) main.logger("Message is null");
            else main.logger(message);
        }
        else {
            if (!message.startsWith("[C]")) player.sendMessage(colorize(player, message));
            else sendCentered(player, message.replace("[C]", ""));
        }
    }

    public void actionBar(Player player, String message) {
        actionBar.getActionBar().send(player, message);
    }

    private boolean checkInts(String[] array) {
        if (array == null) return false;
        for (String integer : array)
            if (!integer.matches("-?\\d+")) return false;
        return true;
    }

    private int[] intArray(String[] array) {
        int[] ints = new int[array.length];
        for (int i = 0; i < array.length; i++)
            ints[i] = Integer.parseInt(array[i]);
        return ints;
    }

    public void title(Player player, String[] message, String[] times) {
        if (message.length == 0 || message.length > 2) return;
        String subtitle = message.length == 1 ? "" : message[1];
        int[] i = checkInts(times) ? intArray(times) : new int[]{10, 50, 10};
        title.getMethod().send(player, message[0], subtitle, i[0], i[1], i[2]);
    }

    // sends with prefix and default placeholders
    public void sendMessage(Player player, Player target, String location) {
        sendMessage(player, target, location, true, true, null, null);
    }

    // sends with prefix and default placeholders
    public void sendMessage(Player player, String location) {
        sendMessage(player, player, location, true, true, null, null);
    }

    // sends with default placeholders and optionally prefix
    public void sendMessage(Player player, String location, boolean prefix) {
        sendMessage(player, player, location, prefix, true, null, null);
    }

    // sends with optional default placeholders and prefix
    public void sendMessage(Player player, String location, boolean prefix, boolean getPlaceholder) {
        sendMessage(player, player, location, prefix, getPlaceholder, null, null);
    }

    // add extra placeholders
    public void sendMessage(Player player, Player target, String location, boolean addPrefix, boolean getPlaceholders, String[] placeholders, String[] replacements) {
        List<String> message = convertList(main.getConfig("lang"), "messages." + location);
        if (message == null || message.isEmpty()) return; // if message does not exist or is empty

        if (getPlaceholders) message.replaceAll(string -> main.getLevelUtils().getPlaceholders(string, target, true));
        if ((placeholders != null) && (placeholders.length == replacements.length))
            message.replaceAll(text -> StringUtils.replaceEach(text, placeholders, replacements));

        if (addPrefix && (prefix != null) && (!prefix.equals("")) && !message.get(0).toLowerCase().startsWith("[actionbar]") &&
                !message.get(0).toLowerCase().startsWith("[title]") && !message.get(0).toLowerCase().startsWith("[c]"))
            message.set(0, prefix + " " + message.get(0));

        if (message.size() == 1 && (message.get(0).equalsIgnoreCase(" ") || message.get(0).equalsIgnoreCase(""))) return;
        for (String s : message) typeMessage(player, s);
    }

    public void sendHelp(Player player, boolean adminHelp) {
        String location = "help-player";
        if (!adminHelp && main.getConfig("lang").getString("messages.help-player") == null) return;
        if (adminHelp) {
            location = "help-admin";
            if (main.getConfig("lang").getString("messages.help-admin") == null) return;
        }
        sendMessage(player, location, false, false);
    }

    public void typeMessage(Player player, String line) {
        if (player == null && !main.getLevelCache().isMessageConsole()) return;
        if (line.toLowerCase().startsWith("[actionbar]"))
            actionBar(player, colorize(player, parseFormat("[actionbar]", line)));
        else if (line.toLowerCase().startsWith("[title]")) {
            title(player, colorize(player, parseFormat("[title]", line)).split("<n>"), null);
        }
        else if (line.toLowerCase().startsWith("[json]") && line.contains("{\"text\":"))
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(), "minecraft:tellraw " +
                    player.getName() + " " + parseFormat("[json]", line)
            );
        else sendMixed(player, line);
    }

    public String parseFormat(String prefix, String line) {
        if (line.toLowerCase().startsWith(prefix))
            line = line.substring(prefix.length());
        while (line.charAt(0) == ' ') line = line.substring(1);
        return line;
    }

}
