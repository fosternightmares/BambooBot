package com.foster.bambooclientbot.commands;

public record CommandRequest(String command, String targetPlayer, int durationTicks, int durationSeconds) {
    public CommandRequest(String command) {
        this(command, "", 0, 0);
    }

    public CommandRequest(String command, String targetPlayer) {
        this(command, targetPlayer, 0, 0);
    }

    public static CommandRequest timedMovement(String command, int durationSeconds) {
        return new CommandRequest(command, "", durationSeconds * 20, durationSeconds);
    }
}
