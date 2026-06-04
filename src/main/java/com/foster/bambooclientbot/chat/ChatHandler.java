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
            String command = parseCommand(messageContent(message, signedMessage), botName);

            if (command == null) {
                return;
            }

            state.queueCommand(new CommandRequest(command));
            BambooClientBot.LOGGER.info("[BambooBot] Command received: {}", command);
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

    private static String parseCommand(String message, String botName) {
        String content = message.trim();

        if (content.startsWith(botName + ", ")) {
            return content.substring(botName.length() + 2).trim();
        }

        if (content.startsWith(botName + " ")) {
            return content.substring(botName.length() + 1).trim();
        }

        return null;
    }
}
