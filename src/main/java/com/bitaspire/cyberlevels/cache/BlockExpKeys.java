package com.bitaspire.cyberlevels.cache;

import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Builds normalized keys for earn-exp block matching (Material + optional {@code age} state).
 */
public final class BlockExpKeys {

    private BlockExpKeys() {}

    /**
     * Returns the material name segment before the first {@code '['}, or the full string if absent.
     *
     * @param key key such as {@code WHEAT} or {@code WHEAT[AGE=7]}
     * @return base material token
     */
    @NotNull
    public static String baseMaterialKey(@NotNull String key) {
        int i = key.indexOf('[');
        return i < 0 ? key : key.substring(0, i);
    }

    /**
     * Normalizes a key from config or runtime (trim + uppercase).
     *
     * @param key raw key
     * @return normalized key
     */
    @NotNull
    public static String normalizeSpecificKey(@NotNull String key) {
        return key.trim().toUpperCase(Locale.ENGLISH);
    }

    /**
     * Builds the lookup key for a block: {@code MATERIAL} or {@code MATERIAL[AGE=n]} for ageable crops.
     *
     * @param block         Bukkit block
     * @param serverVersion {@link com.bitaspire.cyberlevels.CyberLevels#serverVersion()}
     * @return normalized key
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
     * Reads crop age on pre-1.13 servers via {@code org.bukkit.material.Ageable} without a compile-time
     * dependency (some API versions omit that class from the classpath).
     */
    @SuppressWarnings("deprecation")
    private static Integer legacyMaterialAge(@NotNull Block block) {
        try {
            Class<?> ageableClass = Class.forName("org.bukkit.material.Ageable");
            Object data = block.getState().getData();
            if (ageableClass.isInstance(data)) {
                return (Integer) ageableClass.getMethod("getAge").invoke(data);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
