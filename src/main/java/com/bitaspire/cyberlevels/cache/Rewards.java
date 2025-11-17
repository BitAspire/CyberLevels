package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.level.Reward;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import me.croabeast.beanslib.message.MessageSender;
import me.croabeast.file.Configurable;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.IOException;
import java.util.*;

public final class Rewards {

    private final CyberLevels main;
    private int count = 0;

    @Getter
    private final Map<Long, List<Reward>> rewards = new LinkedHashMap<>();

    Rewards(CyberLevels main) {
        this.main = main;

        try {
            long start = System.currentTimeMillis();

            CLVFile file = new CLVFile(main, "rewards");
            for (String key : file.getKeys("rewards")) {
                ConfigurationSection section = file.getSection("rewards." + key);
                if (section == null) continue;

                final RewardImpl reward = new RewardImpl(section);
                for (long level : reward.levels) {
                    List<Reward> list = rewards.computeIfAbsent(level, l -> new ArrayList<>());
                    if (!list.contains(reward)) list.add(reward);
                    rewards.put(level, list);
                }
            }

            main.logger("&7Loaded &e" + (count) + "&7 rewards in &a" + (System.currentTimeMillis() - start) + "ms&7.", "");
        }
        catch (IOException ignored) {}
    }

    class RewardImpl implements Reward {

        private final List<String> commands, messages;

        private final String soundName;
        private final float volume, pitch;

        private final Set<Long> levels = new HashSet<>();

        RewardImpl(ConfigurationSection section) {
            this.commands = Configurable.toStringList(section, "commands");
            this.messages = Configurable.toStringList(section, "messages");
            this.soundName = section.getString("sound.sound-effect");
            this.volume = (float) section.getDouble("sound.volume", 1.0);
            this.pitch = (float) section.getDouble("sound.pitch", 1.0);

            messages.replaceAll(s -> s.replaceAll("(?i)\\[actionbar]", "[action-bar]"));

            for (String level : Configurable.toStringList(section, "levels")) {
                level = level.replace(" ", "");

                if (level.contains(",")) {
                    String[] split = level.split(",");
                    addLevels(Long.parseLong(split[0]), Long.parseLong(split[1]));
                    continue;
                }

                addLevel(Long.parseLong(level));
            }

            count++;
        }

        private void addLevels(long start, long end) {
            for (long i = start; i <= end; i++) addLevel(i);
        }

        private void addLevel(long lvl) {
            levels.add(lvl);
        }

        String parseFormat(String prefix, String line) {
            if (line.toLowerCase().startsWith(prefix)) line = line.substring(prefix.length());
            return line.trim();
        }

        public void executeCommands(Player player) {
            for (String command : commands) {
                command = command.trim();
                if (StringUtils.isBlank(command)) continue;

                if (main.isEnabled("PlaceholderAPI"))
                    command = PlaceholderAPI.setPlaceholders(player, main.levelSystem().replacePlaceholders(command, player.getUniqueId(), false));

                if (command.toLowerCase().startsWith("[player]")) {
                    Bukkit.dispatchCommand(player, parseFormat("[player]", command));
                    continue;
                }

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parseFormat("[console]", command));
            }
        }

        void typeMessage(Player player, String message) {
            new MessageSender(player).setLogger(false).send(message);
        }

        public void sendMessages(Player player) {
            for (String message : messages) {
                message = message.trim();
                if (StringUtils.isBlank(message)) continue;

                message = main.levelSystem().replacePlaceholders(message.replace("[global]", ""), player.getUniqueId(), false);
                if (message.toLowerCase().startsWith("[player]")) {
                    if (isAllowed(player, "player"))
                        typeMessage(player, parseFormat("[player]", message));
                    continue;
                }

                if (!isAllowed(player, "global")) continue;

                String finalMessage = message;
                Bukkit.getOnlinePlayers().forEach(p -> typeMessage(p, finalMessage));
            }
        }

        public void playSound(Player player) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundName), volume, pitch);
            } catch (Exception ignored) {}
        }

        private boolean isAllowed(Player player, String type) {
            if (!player.hasPermission("cyberLevels.suppress.levelup." + type))
                return true;

            for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
                String p = perm.getPermission().toLowerCase();
                if (perm.getValue() && (p.equals("cyberlevels.suppress.levelup." + type)
                        || p.equals("cyberlevels.suppress.levelup.*")
                        || p.equals("cyberlevels.suppress.*"))) return false;
            }
            return true;
        }
    }
}
