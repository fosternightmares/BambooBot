package com.foster.bambooclientbot.reflex;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;

public class DamageDetectionReflex implements Reflex {
    @Override
    public String name() {
        return "damage_detection";
    }

    @Override
    public boolean isEnabled(BotState state) {
        return true;
    }

    @Override
    public void tick(MinecraftClient client, BotState state, ActionExecutor actions) {
        if (client == null || client.player == null) {
            return;
        }

        state.observeHealth(client.player.getHealth());
    }
}
