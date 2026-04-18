package com.bitaspire.cyberlevels.cache;

import java.util.Locale;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Utility methods for building normalized earn-exp lookup keys for blocks.
 *
 * <p>CyberLevels can reward different amounts of EXP for the same material depending on extra
 * state, most notably crop age. This helper centralizes the conversion from Bukkit block data to
 * the normalized string keys stored in the earn-exp configuration, so matching remains consistent
 * across runtime checks, cache loading, and version-specific compatibility branches.
 */
public final class BlockExpKeys {

    private BlockExpKeys() {}

    /**
     * Extracts the material portion of a normalized specific key.
     *
     * <p>This is useful when code needs to compare a fully qualified key such as
     * {@code WHEAT[AGE=7]} against a more general fallback key such as {@code WHEAT}.
     *
     * @param key normalized key such as {@code WHEAT} or {@code WHEAT[AGE=7]}
     * @return base material token without any state suffix
     */
    @NotNull
    public static String baseMaterialKey(@NotNull String key) {
        int i = key.indexOf('[');
        return i < 0 ? key : key.substring(0, i);
    }

    /**
     * Normalizes a config or runtime key into the canonical format used by the earn-exp cache.
     *
     * @param key raw key taken from config or from a runtime block lookup
     * @return trimmed, uppercased key suitable for map lookups
     */
    @NotNull
    public static String normalizeSpecificKey(@NotNull String key) {
        return key.trim().toUpperCase(Locale.ENGLISH);
    }

    /**
     * Builds the earn-exp lookup key for a live Bukkit block.
     *
     * <p>Ageable crops produce a more specific key in the form {@code MATERIAL[AGE=n]} so
     * configurations can distinguish mature crops from immature ones. Non-ageable blocks fall back
     * to the plain material name.
     *
     * @param block Bukkit block to inspect
     * @param serverVersion server version reported by
     *        {@link com.bitaspire.cyberlevels.CyberLevels#serverVersion()}
     * @return normalized key suitable for EXP source matching
     */
    @NotNull
    public static String blockKey(@NotNull Block block, double serverVersion) {
        String mat = block.getType().toString();
        if (serverVersion > 12) {
            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable) {
                org.bukkit.block.data.Ageable ageable =
                        (org.bukkit.block.data.Ageable) block.getBlockData();
                return normalizeSpecificKey(mat + "[age=" + ageable.getAge() + "]");
            }
        } else {
            Integer legacyAge = legacyMaterialAge(block);
            if (legacyAge != null) {
                return normalizeSpecificKey(mat + "[age=" + legacyAge + "]");
            }
        }
        return normalizeSpecificKey(mat);
    }

    /**
     * Attempts to read crop age from legacy pre-1.13 material data without introducing a hard
     * compile-time dependency on classes that may be absent from newer APIs.
     *
     * @param block block whose legacy state should be inspected
     * @return detected age value, or {@code null} when the block is not ageable in the legacy API
     */
    private static Integer legacyMaterialAge(@NotNull Block block) {
        try {
            Class<?> ageableClass = Class.forName("org.bukkit.material.Ageable");
            Object data = block.getState().getData();
            if (ageableClass.isInstance(data)) {
                Object age = ageableClass.getMethod("getAge").invoke(data);
                if (age instanceof Number) {
                    return ((Number) age).intValue();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
