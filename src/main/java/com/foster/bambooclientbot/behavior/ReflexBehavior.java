package com.foster.bambooclientbot.behavior;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.reflex.AutoEatReflex;
import com.foster.bambooclientbot.reflex.ReflexManager;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;

public class ReflexBehavior {
    private final ReflexManager reflexManager = new ReflexManager();

    public ReflexBehavior() {
        reflexManager.register(new AutoEatReflex());
    }

    public void tick(MinecraftClient client, BotState state, ActionExecutor actions) {
        reflexManager.tick(client, state, actions);
    }
}
