package com.foster.bambooclientbot.reflex;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.intent.IntentPlan;
import com.foster.bambooclientbot.intent.IntentRequest;
import com.foster.bambooclientbot.state.BotState;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public interface Reflex {
    String name();

    boolean isEnabled(BotState state);

    List<IntentRequest> collectIntents(MinecraftClient client, BotState state);

    void tick(MinecraftClient client, BotState state, ActionExecutor actions, IntentPlan intentPlan);
}
