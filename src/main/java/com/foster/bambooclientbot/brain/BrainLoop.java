package com.foster.bambooclientbot.brain;

import com.foster.bambooclientbot.actions.ActionRequest;
import com.foster.bambooclientbot.commands.CommandRequest;
import com.foster.bambooclientbot.sensors.PlayerSensors;
import com.foster.bambooclientbot.sensors.WorldSensors;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;

public class BrainLoop {
    private final BotState state;
    private final PlayerSensors playerSensors;
    private final WorldSensors worldSensors;

    public BrainLoop(BotState state, PlayerSensors playerSensors, WorldSensors worldSensors) {
        this.state = state;
        this.playerSensors = playerSensors;
        this.worldSensors = worldSensors;
    }

    public void tick(MinecraftClient client) {
        CommandRequest request;

        while ((request = state.pollCommand()) != null) {
            if ("status".equals(request.command())) {
                state.queueChatMessage(playerSensors.readStatus(client));
            } else if ("nearby".equals(request.command())) {
                state.queueChatMessage(worldSensors.readNearby(client));
            } else if ("invalid_duration".equals(request.command())) {
                state.queueChatMessage("invalid duration");
            } else if ("stop".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.STOP, ""));
            } else if ("forward".equals(request.command())) {
                queueMovement(ActionRequest.ActionType.FORWARD, request);
            } else if ("back".equals(request.command())) {
                queueMovement(ActionRequest.ActionType.BACK, request);
            } else if ("left".equals(request.command())) {
                queueMovement(ActionRequest.ActionType.LEFT, request);
            } else if ("right".equals(request.command())) {
                queueMovement(ActionRequest.ActionType.RIGHT, request);
            } else if ("look_at".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.LOOK_AT_PLAYER, request.targetPlayer()));
            } else if ("approach".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.APPROACH_PLAYER, request.targetPlayer()));
            }
        }
    }

    private void queueMovement(ActionRequest.ActionType actionType, CommandRequest request) {
        state.queueAction(new ActionRequest(actionType, "", request.durationTicks(), request.durationSeconds()));
    }
}
