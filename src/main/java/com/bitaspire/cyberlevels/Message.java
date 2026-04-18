package com.bitaspire.cyberlevels;

import com.bitaspire.libs.common.util.ReplaceUtils;
import com.bitaspire.cyberlevels.cache.Lang;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.bitaspire.libs.takion.message.MessageSender;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Fluent helper for building and sending localized plugin messages.
 *
 * <p>This utility acts as a lightweight message builder around Takion's {@link MessageSender}. It
 * collects message lines, target context, placeholder replacements, and an optional post-processing
 * operator before dispatching everything in one call to {@link #send()}.
 *
 * <p>The class is intentionally short-lived. Typical usage is to create a new instance, configure
 * the desired target and placeholders, and then discard it after sending.
 */
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

    /**
     * Routes the message to a concrete Bukkit player and uses that same player as the parser
     * context for placeholders supported by the underlying messaging library.
     *
     * @param player player that should receive the message
     * @return the same builder instance for chaining
     */
    public Message player(Player player) {
        sender.setTargets(player);
        sender.setParser(player);
        return this;
    }

    /**
     * Resolves the live Bukkit player from a {@link LevelUser} and, when available, delegates to
     * {@link #player(Player)}.
     *
     * <p>If the wrapped user cannot currently provide an online player instance, the call is simply
     * ignored and the builder remains unchanged.
     *
     * @param user level user whose current player should receive the message
     * @return the same builder instance for chaining
     */
    public Message player(LevelUser<?> user) {
        try {
            player(user.getPlayer());
        } catch (Exception ignored) {}
        return this;
    }

    /**
     * Appends multiple raw message lines to the current payload.
     *
     * <p>Null or empty lists are ignored so callers can safely pass optional content without extra
     * guard clauses.
     *
     * @param list message lines to append
     * @return the same builder instance for chaining
     */
    public Message list(List<String> list) {
        if (list != null && !list.isEmpty()) messages.addAll(list);
        return this;
    }

    /**
     * Appends one or more raw message lines to the current payload.
     *
     * @param messages message lines to append
     * @return the same builder instance for chaining
     */
    public Message list(String... messages) {
        if (messages != null) list(Arrays.asList(messages));
        return this;
    }

    /**
     * Resolves message lines from the active {@link Lang} cache and appends them to the payload.
     *
     * <p>This is the most common entry point when sending configurable language messages through a
     * method reference such as {@code Lang::getReloaded}.
     *
     * @param function resolver that extracts a list of lines from {@link Lang}
     * @return the same builder instance for chaining
     */
    public Message list(Function<Lang, List<String>> function) {
        if (lang != null && function != null) list(function.apply(lang));
        return this;
    }

    /**
     * Resolves a single message line from the active {@link Lang} cache and appends it to the
     * payload.
     *
     * @param function resolver that extracts one line from {@link Lang}
     * @return the same builder instance for chaining
     */
    public Message single(Function<Lang, String> function) {
        if (lang != null && function != null)
            list(function.apply(lang));
        return this;
    }

    /**
     * Registers a placeholder replacement that will be applied to every queued line before sending.
     *
     * <p>Keys are normalized to the plugin's brace format, so both {@code player} and
     * {@code {player}} map to the same internal placeholder token.
     *
     * @param key placeholder name, with or without surrounding braces
     * @param value replacement value to render
     * @return the same builder instance for chaining
     */
    public Message placeholder(String key, Object value) {
        if (StringUtils.isNotBlank(key) && value != null) {
            if (!key.startsWith("{")) key = '{' + key + '}';
            placeholders.put(key, value.toString());
        }
        return this;
    }

    /**
     * Starts a placeholder key/value batch definition.
     *
     * <p>The returned helper stores the keys first and then expects a matching call to
     * {@link Values#values(Object...)} so placeholders can be populated in positional order.
     *
     * @param strings placeholder keys that will later receive values
     * @return helper object bound to this builder
     */
    public Values keys(String... strings) {
        Values values = new Values();
        if (strings != null)
            values.keys.addAll(Arrays.asList(strings));
        return values;
    }

    /**
     * Finalizes placeholder replacements, applies the optional output operator, normalizes legacy
     * action-bar tags, and delegates the finished payload to Takion.
     *
     * @return {@code true} when at least one non-blank line was sent successfully
     */
    public boolean send() {
        messages.removeIf(Objects::isNull);

        messages.replaceAll(s -> ReplaceUtils.replaceEach(placeholders, s));
        if (operator != null) messages.replaceAll(operator);

        messages.replaceAll(s -> s.replace("[actionbar]", "[action-bar]"));

        return !messages.isEmpty() &&
                !StringUtils.isBlank(messages.get(0)) &&
                sender.send(messages);
    }

    /**
     * Positional placeholder helper returned by {@link #keys(String...)}.
     *
     * <p>This helper exists purely to keep call sites compact when a message needs several
     * placeholders at once.
     */
    public class Values {

        private final List<String> keys = new ArrayList<>();

        /**
         * Applies the provided values to the keys previously captured by {@link #keys(String...)}.
         *
         * <p>If the number of keys and values does not match, the builder is returned unchanged and
         * no placeholders are written.
         *
         * @param values placeholder values in the same order as the declared keys
         * @return parent {@link Message} builder for continued chaining or sending
         */
        public Message values(Object... values) {
            if (keys.isEmpty() || values == null || keys.size() != values.length)
                return Message.this;

            for (int i = 0; i < keys.size(); i++)
                Message.this.placeholder(keys.get(i), values[i]);

            return Message.this;
        }
    }
}
