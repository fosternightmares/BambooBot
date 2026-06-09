package com.foster.bambooclientbot;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.behavior.ReflexBehavior;
import com.foster.bambooclientbot.brain.BrainLoop;
import com.foster.bambooclientbot.chat.ChatActionExecutor;
import com.foster.bambooclientbot.chat.ChatHandler;
import com.foster.bambooclientbot.intent.IntentPlan;
import com.foster.bambooclientbot.intent.IntentRequest;
import com.foster.bambooclientbot.sensors.ContainerSensor;
import com.foster.bambooclientbot.sensors.LookingAtBlockSensor;
import com.foster.bambooclientbot.sensors.PlayerSensors;
import com.foster.bambooclientbot.sensors.ThreatSensors;
import com.foster.bambooclientbot.sensors.WorldSensors;
import com.foster.bambooclientbot.state.BotState;
import java.util.List;
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
        ReflexBehavior reflexBehavior = new ReflexBehavior();
        ChatActionExecutor chatActionExecutor = new ChatActionExecutor(state);
        LookingAtBlockSensor lookingAtBlockSensor = new LookingAtBlockSensor();
        ThreatSensors threatSensors = new ThreatSensors();
        ContainerSensor containerSensor = new ContainerSensor();

        ChatHandler.register(state);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            lookingAtBlockSensor.update(client, state);
            threatSensors.update(client, state);
            brainLoop.tick(client);
            List<IntentRequest> reflexRequests = reflexBehavior.collectIntents(client, state);
            IntentPlan intentPlan = brainLoop.arbitrateIntents(client, reflexRequests);
            reflexBehavior.tick(client, state, actionExecutor, intentPlan);
            actionExecutor.tick(client);
            containerSensor.update(client, state);
            chatActionExecutor.tick(client);
        });

        LOGGER.info("[BambooClientBot] Loaded");
    }
}
