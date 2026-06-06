package com.foster.bambooclientbot.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class BambooBotLog {
    private static final Path LOG_PATH = Path.of("logs", "bamboobot.log");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BambooBotLog() {
    }

    public static void info(String message) {
        write("INFO", message);
    }

    public static void warn(String message) {
        write("WARN", message);
    }

    public static void error(String message) {
        write("ERROR", message);
    }

    private static synchronized void write(String level, String message) {
        try {
            Files.createDirectories(LOG_PATH.getParent());
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String line = "[" + timestamp + "] [" + level + "] " + sanitize(message) + System.lineSeparator();
            Files.writeString(
                    LOG_PATH,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Dedicated logging must never interrupt the bot loop.
        }
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        return message.replace('\r', ' ').replace('\n', ' ');
    }
}
