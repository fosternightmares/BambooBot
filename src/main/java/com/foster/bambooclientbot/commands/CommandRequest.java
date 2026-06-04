package com.foster.bambooclientbot.commands;

import com.foster.bambooclientbot.state.GotoTarget;

public record CommandRequest(String command, String targetPlayer, int durationTicks, int durationSeconds,
                             String itemId, String itemLabel, int requestedCount, GotoTarget gotoTarget) {
    public CommandRequest(String command) {
        this(command, "", 0, 0, "", "", 0, null);
    }

    public CommandRequest(String command, String targetPlayer) {
        this(command, targetPlayer, 0, 0, "", "", 0, null);
    }

    public static CommandRequest timedMovement(String command, int durationSeconds) {
        return new CommandRequest(command, "", durationSeconds * 20, durationSeconds, "", "", 0, null);
    }

    public static CommandRequest itemTransfer(String command, String itemId, int requestedCount) {
        return new CommandRequest(command, "", 0, 0, itemId, compactItemId(itemId), requestedCount, null);
    }

    public static CommandRequest itemQuery(String command, String itemId, String itemLabel) {
        return new CommandRequest(command, "", 0, 0, itemId, itemLabel, 0, null);
    }

    public static CommandRequest itemQuery(String command, String itemId, String itemLabel, int requestedCount) {
        return new CommandRequest(command, "", 0, 0, itemId, itemLabel, requestedCount, null);
    }

    public static CommandRequest targetedItem(String command, String targetPlayer, String itemId, String itemLabel, int requestedCount) {
        return new CommandRequest(command, targetPlayer, 0, 0, itemId, itemLabel, requestedCount, null);
    }

    public static CommandRequest gotoCoordinates(GotoTarget target) {
        return new CommandRequest("goto", "", 0, 0, "", "", 0, target);
    }

    private static String compactItemId(String itemId) {
        if (itemId.startsWith("minecraft:")) {
            return itemId.substring("minecraft:".length());
        }

        return itemId;
    }
}
