package com.foster.bambooclientbot.commands;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ItemResolver {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH_PATTERN = Pattern.compile("[a-z0-9_./-]+");

    private ItemResolver() {
    }

    public static String resolveItemArg(String rawItemText) {
        String normalized = rawItemText.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");

        if (normalized.isBlank()) {
            return "";
        }

        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        return isValidItemId(normalized) ? normalized : "";
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
