package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.BotState;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;

class SimpleMovementController {
    private static final double APPROACH_TARGET_DISTANCE = 2.5;

    private final BotState state;
    private final LookController lookController;
    private final RouteExecutor routeExecutor;
    private final MovementController movementController;
    private ActionRequest timedMovement;
    private int timedMovementTicksRemaining;
    private ActionRequest activeApproach;

    SimpleMovementController(BotState state, LookController lookController, RouteExecutor routeExecutor,
                             MovementController movementController) {
        this.state = state;
        this.lookController = lookController;
        this.routeExecutor = routeExecutor;
        this.movementController = movementController;
    }

    void tickApproach(MinecraftClient client, Consumer<MinecraftClient> stopMovement,
                      BiFunction<ClientPlayerEntity, String, AbstractClientPlayerEntity> targetFinder) {
        if (activeApproach == null) {
            return;
        }

        if (client == null || client.player == null || client.world == null || client.options == null) {
            activeApproach.setStatus(ActionRequest.ActionStatus.FAILED);
            activeApproach = null;
            stopMovement.accept(client);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = targetFinder.apply(player, activeApproach.actionData());

        if (target == null) {
            activeApproach.setStatus(ActionRequest.ActionStatus.FAILED);
            activeApproach = null;
            stopMovement.accept(client);
            state.queueChatMessage("player not found");
            return;
        }

        if (player.distanceTo(target) <= APPROACH_TARGET_DISTANCE) {
            stopMovement.accept(client);
            activeApproach.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.queueChatMessage("arrived near " + activeApproach.actionData());
            activeApproach = null;
            return;
        }

        lookController.rotateToward(player, target.getEyePos());
        movementController.pressForward(client.options, "approach_tick");
    }

    void tickTimedMovement(MinecraftClient client, Consumer<MinecraftClient> stopMovement) {
        if (timedMovement == null) {
            return;
        }

        if (client == null || client.player == null || client.options == null) {
            timedMovement.setStatus(ActionRequest.ActionStatus.FAILED);
            timedMovement = null;
            timedMovementTicksRemaining = 0;
            state.queueChatMessage("action failed");
            return;
        }

        timedMovementTicksRemaining--;

        if (timedMovementTicksRemaining <= 0) {
            stopMovement.accept(client);
            timedMovement.setStatus(ActionRequest.ActionStatus.COMPLETE);
            timedMovement = null;
            state.queueChatMessage("movement complete");
        }
    }

    void move(MinecraftClient client, ActionRequest request, String direction, Runnable cancelConflictingMovement) {
        if (client == null || client.player == null || client.options == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
            return;
        }

        activeApproach = null;
        cancelConflictingMovement.run();
        routeExecutor.resetJumpCooldown();
        state.setFollowJump(false);
        movementController.applyManualMovement(client.options, request.actionType());

        if (request.durationTicks() > 0) {
            timedMovement = request;
            timedMovementTicksRemaining = request.durationTicks();
            state.queueChatMessage("moving " + direction + " for " + request.durationSeconds() + "s");
        } else {
            timedMovement = null;
            timedMovementTicksRemaining = 0;
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.queueChatMessage("moving " + direction);
        }
    }

    void startApproach(MinecraftClient client, ActionRequest request, Consumer<MinecraftClient> stopMovement,
                       BiFunction<ClientPlayerEntity, String, AbstractClientPlayerEntity> targetFinder,
                       Runnable cancelConflictingMovement) {
        if (client == null || client.player == null || client.world == null || client.options == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = targetFinder.apply(player, request.actionData());

        if (target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("player not found");
            return;
        }

        if (player.distanceTo(target) <= APPROACH_TARGET_DISTANCE) {
            stopMovement.accept(client);
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.queueChatMessage("arrived near " + request.actionData());
            return;
        }

        timedMovement = null;
        timedMovementTicksRemaining = 0;
        cancelConflictingMovement.run();
        routeExecutor.resetJumpCooldown();
        state.setFollowJump(false);
        lookController.rotateToward(player, target.getEyePos());
        movementController.applyForwardNudge(client.options);
        activeApproach = request;
        state.queueChatMessage("approaching " + request.actionData());
    }

    void cancelActiveMovement() {
        activeApproach = null;
        timedMovement = null;
        timedMovementTicksRemaining = 0;
    }

    void cancelApproach() {
        activeApproach = null;
    }

    void cancelTimedMovement() {
        timedMovement = null;
        timedMovementTicksRemaining = 0;
    }

}
