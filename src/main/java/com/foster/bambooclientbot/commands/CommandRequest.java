package com.foster.bambooclientbot.commands;

public record CommandRequest(String command, String targetPlayer, int durationTicks, int durationSeconds,
                             String itemId, String itemLabel, int requestedCount) {
    public CommandRequest(String command) {
        this(command, "", 0, 0, "", "", 0);
    }

    public CommandRequest(String command, String targetPlayer) {
        this(command, targetPlayer, 0, 0, "", "", 0);
    }

    public static CommandRequest timedMovement(String command, int durationSeconds) {
        return new CommandRequest(command, "", durationSeconds * 20, durationSeconds, "", "", 0);
    }

    public static CommandRequest itemTransfer(String command, String itemId, int requestedCount) {
        return new CommandRequest(command, "", 0, 0, itemId, compactItemId(itemId), requestedCount);
    }

    public static CommandRequest itemQuery(String command, String itemId, String itemLabel) {
        return new CommandRequest(command, "", 0, 0, itemId, itemLabel, 0);
    }

    private static String compactItemId(String itemId) {
        if (itemId.startsWith("minecraft:")) {
            return itemId.substring("minecraft:".length());
        }

        return itemId;
    }
}
