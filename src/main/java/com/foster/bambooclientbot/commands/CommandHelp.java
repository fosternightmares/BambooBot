package com.foster.bambooclientbot.commands;

import java.util.List;

public final class CommandHelp {
    private static final int MAX_HELP_LENGTH = 240;
    private static final List<String> VISIBLE_COMMANDS = List.of(
            "status",
            "nearby",
            "help",
            "stop",
            "forward",
            "back",
            "left",
            "right",
            "look at me",
            "approach me",
            "follow me",
            "open",
            "close",
            "snapshot",
            "snapshot details",
            "inventory",
            "inventory details",
            "count <item>",
            "deposit <item> <count>",
            "withdraw <item> <count>"
    );

    private CommandHelp() {
    }

    public static String format() {
        if (VISIBLE_COMMANDS.isEmpty()) {
            return "commands unavailable";
        }

        String prefix = "commands: ";
        StringBuilder message = new StringBuilder(prefix);
        int includedCommands = 0;

        for (String command : VISIBLE_COMMANDS) {
            int separatorLength = includedCommands == 0 ? 0 : 2;
            int remainingAfterCommand = VISIBLE_COMMANDS.size() - includedCommands - 1;
            String truncationSuffix = remainingAfterCommand > 0 ? ", ...+" + remainingAfterCommand : "";

            if (message.length() + separatorLength + command.length() + truncationSuffix.length() > MAX_HELP_LENGTH) {
                break;
            }

            if (includedCommands > 0) {
                message.append(", ");
            }

            message.append(command);
            includedCommands++;
        }

        if (includedCommands < VISIBLE_COMMANDS.size()) {
            if (includedCommands > 0) {
                message.append(", ");
            }

            message.append("...+").append(VISIBLE_COMMANDS.size() - includedCommands);
        }

        return message.toString();
    }
}
