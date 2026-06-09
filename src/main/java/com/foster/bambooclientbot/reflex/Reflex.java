package com.foster.bambooclientbot.reflex;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;

public interface Reflex {
    String name();

    boolean isEnabled(BotState state);

    void tick(MinecraftClient client, BotState state, ActionExecutor actions);
}
