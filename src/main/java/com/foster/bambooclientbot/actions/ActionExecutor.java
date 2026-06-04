package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.LookedAtBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.Locale;

public class ActionExecutor {
    private static final double LOOK_AT_RANGE = 32.0;
    private static final double APPROACH_TARGET_DISTANCE = 2.5;
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double FOLLOW_SPRINT_ENABLE_DISTANCE = 6.0;
    private static final double FOLLOW_SPRINT_DISABLE_DISTANCE = 4.0;

    private final BotState state;
    private ActionRequest timedMovement;
    private int timedMovementTicksRemaining;
    private ActionRequest activeApproach;
    private ActionRequest activeFollow;

    public ActionExecutor(BotState state) {
        this.state = state;
    }

    public void tick(MinecraftClient client) {
        ActionRequest request = state.pollAction();

        if (request != null) {
            executeAction(client, request);
            return;
        }

        tickApproach(client);
        tickFollow(client);
        tickTimedMovement(client);
    }

    private void executeAction(MinecraftClient client, ActionRequest request) {
        request.setStatus(ActionRequest.ActionStatus.RUNNING);
        state.setActiveAction(request.actionType().name());

        try {
            if (request.actionType() == ActionRequest.ActionType.STOP) {
                activeApproach = null;
                activeFollow = null;
                timedMovement = null;
                timedMovementTicksRemaining = 0;
                stop(client);
                request.setStatus(ActionRequest.ActionStatus.COMPLETE);
                state.queueChatMessage("stopped");
            } else if (request.actionType() == ActionRequest.ActionType.FORWARD) {
                move(client, request, "forward");
            } else if (request.actionType() == ActionRequest.ActionType.BACK) {
                move(client, request, "back");
            } else if (request.actionType() == ActionRequest.ActionType.LEFT) {
                move(client, request, "left");
            } else if (request.actionType() == ActionRequest.ActionType.RIGHT) {
                move(client, request, "right");
            } else if (request.actionType() == ActionRequest.ActionType.LOOK_AT_PLAYER) {
                if (lookAtPlayer(client, request.actionData())) {
                    request.setStatus(ActionRequest.ActionStatus.COMPLETE);
                    state.queueChatMessage("looking at " + request.actionData());
                } else {
                    request.setStatus(ActionRequest.ActionStatus.FAILED);
                    state.queueChatMessage("player not found");
                }
            } else if (request.actionType() == ActionRequest.ActionType.APPROACH_PLAYER) {
                startApproach(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.FOLLOW_PLAYER) {
                startFollow(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.INTERACT_TARGETED_BLOCK) {
                interactTargetedBlock(client, request);
            }
        } catch (Exception exception) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "exception");
            state.queueChatMessage("action failed");
        } finally {
            state.clearActiveAction();
        }
    }

    private void interactTargetedBlock(MinecraftClient client, ActionRequest request) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "client_unavailable");
            state.queueChatMessage("action failed");
            return;
        }

        LookedAtBlock target = request.targetedBlock();

        if (target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "no_target_block");
            state.queueChatMessage("action failed");
            return;
        }

        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());

        if (client.world.getBlockState(pos).isAir()) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "no_target_block");
            state.queueChatMessage("action failed");
            return;
        }

        Direction side = Direction.valueOf(target.side().toUpperCase(Locale.ROOT));
        Vec3d hitPos = new Vec3d(target.hitX(), target.hitY(), target.hitZ());
        BlockHitResult hitResult = new BlockHitResult(hitPos, side, pos, false);
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);

        if (result.isAccepted()) {
            client.player.swingHand(Hand.MAIN_HAND);
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.setLastActionResult(request.actionType().name(), "success", "");
            state.queueChatMessage("interacted");
        } else {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "not_accepted");
            state.queueChatMessage("action failed");
        }
    }

    private void tickApproach(MinecraftClient client) {
        if (activeApproach == null) {
            return;
        }

        if (client == null || client.player == null || client.world == null || client.options == null) {
            activeApproach.setStatus(ActionRequest.ActionStatus.FAILED);
            activeApproach = null;
            stop(client);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, activeApproach.actionData());

        if (target == null) {
            activeApproach.setStatus(ActionRequest.ActionStatus.FAILED);
            activeApproach = null;
            stop(client);
            state.queueChatMessage("player not found");
            return;
        }

        if (player.distanceTo(target) <= APPROACH_TARGET_DISTANCE) {
            stop(client);
            activeApproach.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.queueChatMessage("arrived near " + activeApproach.actionData());
            activeApproach = null;
            return;
        }

        rotateToward(player, target.getEyePos());
        client.options.forwardKey.setPressed(true);
    }

    private void tickFollow(MinecraftClient client) {
        if (activeFollow == null) {
            return;
        }

        if (client == null || client.player == null || client.world == null || client.options == null) {
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            stop(client);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, activeFollow.actionData());

        if (target == null) {
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            stop(client);
            state.queueChatMessage("player not found");
            return;
        }

        rotateToward(player, target.getEyePos());

        if (player.distanceTo(target) > FOLLOW_DISTANCE) {
            clearDirectionalKeys(client.options);
            client.options.forwardKey.setPressed(true);
        } else {
            clearDirectionalKeys(client.options);
        }

        updateFollowSprint(client.options, player.distanceTo(target));
    }

    private void tickTimedMovement(MinecraftClient client) {
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
            stop(client);
            timedMovement.setStatus(ActionRequest.ActionStatus.COMPLETE);
            timedMovement = null;
            state.queueChatMessage("movement complete");
        }
    }

    private void move(MinecraftClient client, ActionRequest request, String direction) {
        if (client == null || client.player == null || client.options == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
            return;
        }

        activeApproach = null;
        activeFollow = null;
        GameOptions options = client.options;
        clearDirectionalKeys(options);
        options.sprintKey.setPressed(false);
        movementKey(options, request.actionType()).setPressed(true);

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

    private void startApproach(MinecraftClient client, ActionRequest request) {
        if (client == null || client.player == null || client.world == null || client.options == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, request.actionData());

        if (target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("player not found");
            return;
        }

        if (player.distanceTo(target) <= APPROACH_TARGET_DISTANCE) {
            stop(client);
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.queueChatMessage("arrived near " + request.actionData());
            return;
        }

        timedMovement = null;
        timedMovementTicksRemaining = 0;
        activeFollow = null;
        clearDirectionalKeys(client.options);
        client.options.sprintKey.setPressed(false);
        rotateToward(player, target.getEyePos());
        client.options.forwardKey.setPressed(true);
        activeApproach = request;
        state.queueChatMessage("approaching " + request.actionData());
    }

    private void startFollow(MinecraftClient client, ActionRequest request) {
        if (client == null || client.player == null || client.world == null || client.options == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, request.actionData());

        if (target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("player not found");
            return;
        }

        timedMovement = null;
        timedMovementTicksRemaining = 0;
        activeApproach = null;
        clearDirectionalKeys(client.options);
        client.options.sprintKey.setPressed(false);
        rotateToward(player, target.getEyePos());

        if (player.distanceTo(target) > FOLLOW_DISTANCE) {
            client.options.forwardKey.setPressed(true);
        }

        updateFollowSprint(client.options, player.distanceTo(target));
        activeFollow = request;
        state.queueChatMessage("following " + request.actionData());
    }

    private void stop(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        GameOptions options = client.options;
        clearDirectionalKeys(options);
        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }

    private void updateFollowSprint(GameOptions options, double targetDistance) {
        if (targetDistance > FOLLOW_SPRINT_ENABLE_DISTANCE) {
            options.sprintKey.setPressed(true);
        } else if (targetDistance <= FOLLOW_SPRINT_DISABLE_DISTANCE) {
            options.sprintKey.setPressed(false);
        }
    }

    private void clearDirectionalKeys(GameOptions options) {
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
    }

    private KeyBinding movementKey(GameOptions options, ActionRequest.ActionType actionType) {
        return switch (actionType) {
            case FORWARD -> options.forwardKey;
            case BACK -> options.backKey;
            case LEFT -> options.leftKey;
            case RIGHT -> options.rightKey;
            default -> throw new IllegalArgumentException("Not a movement action: " + actionType);
        };
    }

    private boolean lookAtPlayer(MinecraftClient client, String targetPlayerName) {
        if (client == null || client.player == null || client.world == null || targetPlayerName == null || targetPlayerName.isBlank()) {
            return false;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, targetPlayerName);

        if (target == null) {
            return false;
        }

        rotateToward(player, target.getEyePos());
        return true;
    }

    private AbstractClientPlayerEntity findTargetPlayer(MinecraftClient client, ClientPlayerEntity player, String targetPlayerName) {
        for (AbstractClientPlayerEntity candidate : client.world.getPlayers()) {
            if (candidate == player || !candidate.isAlive() || candidate.isInvisibleTo(player)) {
                continue;
            }

            if (player.distanceTo(candidate) > LOOK_AT_RANGE) {
                continue;
            }

            if (candidate.getGameProfile().name().equalsIgnoreCase(targetPlayerName)) {
                return candidate;
            }
        }

        return null;
    }

    private void rotateToward(ClientPlayerEntity player, Vec3d targetPosition) {
        Vec3d eyePosition = player.getEyePos();
        double deltaX = targetPosition.x - eyePosition.x;
        double deltaY = targetPosition.y - eyePosition.y;
        double deltaZ = targetPosition.z - eyePosition.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }
}
