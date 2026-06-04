package com.foster.bambooclientbot.commands;

public record CommandRequest(String command, String targetPlayer) {
    public CommandRequest(String command) {
        this(command, "");
    }
}
