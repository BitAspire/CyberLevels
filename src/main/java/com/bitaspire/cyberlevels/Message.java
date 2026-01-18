package com.bitaspire.cyberlevels;

import com.bitaspire.common.util.ReplaceUtils;
import com.bitaspire.cyberlevels.cache.Lang;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.bitaspire.takion.message.MessageSender;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class Message {

    private final Lang lang = CyberLevels.instance().cache().lang();

    private final List<String> messages = new ArrayList<>();
    @Accessors(chain = true, fluent = true)
    @Setter
    private UnaryOperator<String> operator = null;

    private final Map<String, String> placeholders = new LinkedHashMap<>();
    private final MessageSender sender = CyberLevels.instance()
            .library().getLoadedSender()
            .setLogger(false).setSensitive(false);

    public Message player(Player player) {
        sender.setTargets(player);
        sender.setParser(player);
        return this;
    }

    public Message player(LevelUser<?> user) {
        try {
            player(user.getPlayer());
        } catch (Exception ignored) {}
        return this;
    }

    public Message list(List<String> list) {
        if (list != null && !list.isEmpty()) messages.addAll(list);
        return this;
    }

    public Message list(String... messages) {
        if (messages != null) list(Arrays.asList(messages));
        return this;
    }

    public Message list(Function<Lang, List<String>> function) {
        if (lang != null && function != null) list(function.apply(lang));
        return this;
    }

    public Message single(Function<Lang, String> function) {
        if (lang != null && function != null)
            list(function.apply(lang));
        return this;
    }

    public Message placeholder(String key, Object value) {
        if (StringUtils.isNotBlank(key) && value != null) {
            if (!key.startsWith("{")) key = '{' + key + '}';
            placeholders.put(key, value.toString());
        }
        return this;
    }

    public Values keys(String... strings) {
        Values values = new Values();
        if (strings != null)
            values.keys.addAll(Arrays.asList(strings));
        return values;
    }

    public boolean send() {
        messages.removeIf(Objects::isNull);

        messages.replaceAll(s -> ReplaceUtils.replaceEach(placeholders, s));
        if (operator != null) messages.replaceAll(operator);

        messages.replaceAll(s -> s.replace("[actionbar]", "[action-bar]"));

        return !messages.isEmpty() &&
                !StringUtils.isBlank(messages.get(0)) &&
                sender.send(messages);
    }

    public class Values {

        private final List<String> keys = new ArrayList<>();

        public Message values(Object... values) {
            if (keys.isEmpty() || values == null || keys.size() != values.length)
                return Message.this;

            for (int i = 0; i < keys.size(); i++)
                Message.this.placeholder(keys.get(i), values[i]);

            return Message.this;
        }
    }
}