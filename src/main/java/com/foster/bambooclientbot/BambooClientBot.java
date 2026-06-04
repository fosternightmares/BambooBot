package com.foster.bambooclientbot;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.brain.BrainLoop;
import com.foster.bambooclientbot.chat.ChatActionExecutor;
import com.foster.bambooclientbot.chat.ChatHandler;
import com.foster.bambooclientbot.sensors.PlayerSensors;
import com.foster.bambooclientbot.sensors.WorldSensors;
import com.foster.bambooclientbot.state.BotState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BambooClientBot implements ClientModInitializer {
    public static final String MOD_ID = "bambooclientbot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        BotState state = new BotState();
        BrainLoop brainLoop = new BrainLoop(state, new PlayerSensors(), new WorldSensors());
        ActionExecutor actionExecutor = new ActionExecutor(state);
        ChatActionExecutor chatActionExecutor = new ChatActionExecutor(state);

        ChatHandler.register(state);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            brainLoop.tick(client);
            actionExecutor.tick(client);
            chatActionExecutor.tick(client);
        });

        LOGGER.info("[BambooClientBot] Loaded");
    }
}
