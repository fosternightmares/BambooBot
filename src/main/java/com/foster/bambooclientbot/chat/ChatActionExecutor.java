package com.foster.bambooclientbot.chat;

import com.foster.bambooclientbot.BambooClientBot;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class ChatActionExecutor {
    private final BotState state;

    public ChatActionExecutor(BotState state) {
        this.state = state;
    }

    public void tick(MinecraftClient client) {
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();

        if (networkHandler == null) {
            return;
        }

        String message = state.pollChatMessage();

        if (message == null) {
            return;
        }

        networkHandler.sendChatMessage(message);
        BambooClientBot.LOGGER.info("[BambooBot] Chat response sent");
    }
}
