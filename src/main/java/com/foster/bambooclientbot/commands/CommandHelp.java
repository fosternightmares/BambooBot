package com.foster.bambooclientbot.commands;

import java.util.List;

public final class CommandHelp {
    private static final int MAX_HELP_LENGTH = 240;
    private static final List<String> VISIBLE_COMMANDS = List.of(
            "status",
            "movement",
            "route",
            "follow status",
            "inventory status",
            "container status",
            "threats",
            "sensors",
            "nearby",
            "nav check",
            "path <x> <y> <z>",
            "help",
            "stop",
            "forward",
            "back",
            "left",
            "right",
            "goto <x> <y> <z>",
            "look at me",
            "approach me",
            "follow me",
            "autoswing on",
            "autoswing off",
            "autouse on",
            "autouse off",
            "autosneak on",
            "autosneak off",
            "farm off",
            "actions off",
            "open",
            "close",
            "snapshot",
            "snapshot details",
            "inventory",
            "inventory details",
            "count <item>",
            "have <item> <count>",
            "drop <item> <count>",
            "give me <item> <count>",
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
