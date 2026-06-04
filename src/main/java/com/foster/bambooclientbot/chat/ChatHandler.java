package com.foster.bambooclientbot.chat;

import com.foster.bambooclientbot.BambooClientBot;
import com.foster.bambooclientbot.commands.CommandRequest;
import com.foster.bambooclientbot.commands.ItemResolver;
import com.foster.bambooclientbot.state.BotState;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;

public final class ChatHandler {
    private static final int MIN_DURATION_SECONDS = 1;
    private static final int MAX_DURATION_SECONDS = 10;

    private ChatHandler() {
    }

    public static void register(BotState state) {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String botName = botName(MinecraftClient.getInstance());
            CommandRequest command = parseCommand(messageContent(message, signedMessage), botName, senderName(sender));

            if (command == null) {
                return;
            }

            state.queueCommand(command);
            BambooClientBot.LOGGER.info("[BambooBot] Command received: {}", command.command());
        });
    }

    private static String botName(MinecraftClient client) {
        if (client.player != null) {
            return client.player.getGameProfile().name();
        }

        return client.getSession().getUsername();
    }

    private static String senderName(GameProfile sender) {
        if (sender == null) {
            return "";
        }

        return sender.name();
    }

    private static String messageContent(Text message, SignedMessage signedMessage) {
        if (signedMessage != null) {
            return signedMessage.getSignedContent();
        }

        return message.getString();
    }

    private static CommandRequest parseCommand(String message, String botName, String senderName) {
        String content = message.trim();
        String command;

        if (content.startsWith(botName + ", ")) {
            command = content.substring(botName.length() + 2).trim();
        } else if (content.startsWith(botName + " ")) {
            command = content.substring(botName.length() + 1).trim();
        } else {
            return null;
        }

        if (command.startsWith("look at ")) {
            String targetPlayer = command.substring("look at ".length()).trim();

            if (targetPlayer.isEmpty()) {
                return null;
            }

            return new CommandRequest("look_at", resolvePlayerArg(targetPlayer, senderName));
        }

        if (command.startsWith("approach ")) {
            String targetPlayer = command.substring("approach ".length()).trim();

            if (targetPlayer.isEmpty()) {
                return null;
            }

            return new CommandRequest("approach", resolvePlayerArg(targetPlayer, senderName));
        }

        if (command.startsWith("follow ")) {
            String targetPlayer = command.substring("follow ".length()).trim();

            if (targetPlayer.isEmpty()) {
                return null;
            }

            return new CommandRequest("follow", resolvePlayerArg(targetPlayer, senderName));
        }

        if (command.startsWith("deposit ")) {
            return parseItemTransfer("deposit", command.substring("deposit ".length()));
        }

        if ("deposit".equals(command)) {
            return new CommandRequest("invalid_count");
        }

        if (command.startsWith("withdraw ")) {
            return parseItemTransfer("withdraw", command.substring("withdraw ".length()));
        }

        if ("withdraw".equals(command)) {
            return new CommandRequest("invalid_count");
        }

        if (command.startsWith("count ")) {
            return parseItemCount(command.substring("count ".length()));
        }

        if ("count".equals(command)) {
            return new CommandRequest("invalid_count_item");
        }

        if (command.startsWith("have ")) {
            return parseHaveItem(command.substring("have ".length()));
        }

        if ("have".equals(command)) {
            return new CommandRequest("invalid_have");
        }

        String[] parts = command.split("\\s+");

        if (parts.length > 1 && isMovementCommand(parts[0])) {
            if (parts.length != 2) {
                return new CommandRequest("invalid_duration");
            }

            return parseTimedMovement(parts[0], parts[1]);
        }

        return new CommandRequest(command);
    }

    private static String resolvePlayerArg(String arg, String senderName) {
        if ("me".equalsIgnoreCase(arg) && !senderName.isBlank()) {
            return senderName;
        }

        return arg;
    }

    private static boolean isMovementCommand(String command) {
        return "forward".equals(command)
                || "back".equals(command)
                || "left".equals(command)
                || "right".equals(command);
    }

    private static CommandRequest parseTimedMovement(String command, String durationText) {
        try {
            int durationSeconds = Integer.parseInt(durationText);

            if (durationSeconds < MIN_DURATION_SECONDS || durationSeconds > MAX_DURATION_SECONDS) {
                return new CommandRequest("invalid_duration");
            }

            return CommandRequest.timedMovement(command, durationSeconds);
        } catch (NumberFormatException exception) {
            return new CommandRequest("invalid_duration");
        }
    }

    private static CommandRequest parseItemTransfer(String command, String transferText) {
        String trimmed = transferText.trim();
        int countStart = trimmed.lastIndexOf(' ');

        if (countStart <= 0 || countStart >= trimmed.length() - 1) {
            return new CommandRequest("invalid_count");
        }

        String rawItemText = trimmed.substring(0, countStart);
        String countText = trimmed.substring(countStart + 1);
        String itemId = ItemResolver.resolveItemArg(rawItemText);

        if (itemId.isBlank()) {
            return new CommandRequest("invalid_item");
        }

        try {
            int requestedCount = Integer.parseInt(countText);

            if (requestedCount <= 0) {
                return new CommandRequest("invalid_count");
            }

            return CommandRequest.itemTransfer(command, itemId, requestedCount);
        } catch (NumberFormatException exception) {
            return new CommandRequest("invalid_count");
        }
    }

    private static CommandRequest parseItemCount(String itemText) {
        String itemId = ItemResolver.resolveItemArg(itemText);

        if (itemId.isBlank()) {
            return new CommandRequest("invalid_count_item");
        }

        return CommandRequest.itemQuery("count", itemId, ItemResolver.displayItemArg(itemText));
    }

    private static CommandRequest parseHaveItem(String itemText) {
        String trimmed = itemText.trim();

        if (trimmed.isBlank()) {
            return new CommandRequest("invalid_have");
        }

        int requestedCount = 1;
        String rawItemText = trimmed;
        int countStart = trimmed.lastIndexOf(' ');

        if (countStart > 0 && countStart < trimmed.length() - 1) {
            String possibleCount = trimmed.substring(countStart + 1);

            try {
                requestedCount = Integer.parseInt(possibleCount);

                if (requestedCount <= 0) {
                    return new CommandRequest("invalid_have");
                }

                rawItemText = trimmed.substring(0, countStart);
            } catch (NumberFormatException exception) {
                rawItemText = trimmed;
            }
        }

        String itemId = ItemResolver.resolveItemArg(rawItemText);

        if (itemId.isBlank()) {
            return new CommandRequest("invalid_have");
        }

        return CommandRequest.itemQuery("have", itemId, ItemResolver.displayItemArg(rawItemText), requestedCount);
    }
}
