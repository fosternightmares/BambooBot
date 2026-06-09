package com.foster.bambooclientbot.behavior;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.intent.IntentPlan;
import com.foster.bambooclientbot.intent.IntentRequest;
import com.foster.bambooclientbot.reflex.AutoEatReflex;
import com.foster.bambooclientbot.reflex.DamageAwarenessReflex;
import com.foster.bambooclientbot.reflex.ReflexManager;
import java.util.List;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;

public class ReflexBehavior {
    private final ReflexManager reflexManager = new ReflexManager();

    public ReflexBehavior() {
        reflexManager.register(new DamageAwarenessReflex());
        reflexManager.register(new AutoEatReflex());
    }

    public List<IntentRequest> collectIntents(MinecraftClient client, BotState state) {
        return reflexManager.collectIntents(client, state);
    }

    public void tick(MinecraftClient client, BotState state, ActionExecutor actions, IntentPlan intentPlan) {
        reflexManager.tick(client, state, actions, intentPlan);
    }
}
