package com.foster.bambooclientbot.brain;

import com.foster.bambooclientbot.commands.CommandRequest;
import com.foster.bambooclientbot.sensors.PlayerSensors;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;

public class BrainLoop {
    private final BotState state;
    private final PlayerSensors playerSensors;

    public BrainLoop(BotState state, PlayerSensors playerSensors) {
        this.state = state;
        this.playerSensors = playerSensors;
    }

    public void tick(MinecraftClient client) {
        CommandRequest request;

        while ((request = state.pollCommand()) != null) {
            if ("status".equals(request.command())) {
                state.queueChatMessage(playerSensors.readStatus(client));
            }
        }
    }
}
