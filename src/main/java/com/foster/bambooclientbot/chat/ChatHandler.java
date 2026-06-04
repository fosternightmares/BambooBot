package com.foster.bambooclientbot.chat;

import com.foster.bambooclientbot.BambooClientBot;
import com.foster.bambooclientbot.commands.CommandRequest;
import com.foster.bambooclientbot.state.BotState;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;

public final class ChatHandler {
    private ChatHandler() {
    }

    public static void register(BotState state) {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String botName = botName(MinecraftClient.getInstance());
            CommandRequest command = parseCommand(messageContent(message, signedMessage), botName);

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

    private static String messageContent(Text message, SignedMessage signedMessage) {
        if (signedMessage != null) {
            return signedMessage.getSignedContent();
        }

        return message.getString();
    }

    private static CommandRequest parseCommand(String message, String botName) {
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

            return new CommandRequest("look_at", targetPlayer);
        }

        return new CommandRequest(command);
    }
}
