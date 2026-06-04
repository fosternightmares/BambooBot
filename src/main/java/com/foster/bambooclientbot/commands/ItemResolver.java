package com.foster.bambooclientbot.commands;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ItemResolver {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH_PATTERN = Pattern.compile("[a-z0-9_./-]+");
    private static final Map<String, String> ALIASES = Map.of(
            "rocket", "firework_rocket",
            "rockets", "firework_rocket"
    );

    private ItemResolver() {
    }

    public static String resolveItemArg(String rawItemText) {
        String normalized = rawItemText.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");

        if (normalized.isBlank()) {
            return "";
        }

        normalized = ALIASES.getOrDefault(normalized, normalized);

        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        return isValidItemId(normalized) ? normalized : "";
    }

    public static String displayItemArg(String rawItemText) {
        return rawItemText.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    private static boolean isValidItemId(String itemId) {
        String[] parts = itemId.split(":", -1);

        if (parts.length != 2) {
            return false;
        }

        return NAMESPACE_PATTERN.matcher(parts[0]).matches()
                && PATH_PATTERN.matcher(parts[1]).matches();
    }
}
