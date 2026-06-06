package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;

class RouteExecutor {
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double FOLLOW_SPRINT_ENABLE_DISTANCE = 6.0;
    private static final double FOLLOW_SPRINT_DISABLE_DISTANCE = 4.0;
    private static final double GOTO_JUMP_TARGET_Y_DELTA = 0.5;
    private static final int FOLLOW_JUMP_COOLDOWN_TICKS = 8;

    private final BotState state;
    private int jumpCooldownTicks;

    RouteExecutor(BotState state) {
        this.state = state;
    }

    void tickJumpCooldown() {
        if (jumpCooldownTicks > 0) {
            jumpCooldownTicks--;
        }
    }

    void resetJumpCooldown() {
        jumpCooldownTicks = 0;
    }

    boolean pressJumpIfReady(GameOptions options) {
        if (jumpCooldownTicks > 0) {
            return false;
        }

        options.jumpKey.setPressed(true);
        state.setFollowJump(true);
        jumpCooldownTicks = FOLLOW_JUMP_COOLDOWN_TICKS;
        return true;
    }

    void updateSprint(GameOptions options, double targetDistance) {
        if (targetDistance > FOLLOW_SPRINT_ENABLE_DISTANCE) {
            options.sprintKey.setPressed(true);
        } else if (targetDistance <= FOLLOW_SPRINT_DISABLE_DISTANCE) {
            options.sprintKey.setPressed(false);
        }
    }

    boolean executeFollowRoute(GameOptions options, ClientPlayerEntity player, BlockPos waypoint,
                               double targetDistance) {
        clearDirectionalKeys(options);
        options.forwardKey.setPressed(true);
        boolean jumpWanted = followRouteJumpWanted(player, waypoint);
        updateFollowRouteJump(options, player, jumpWanted);
        updateSprint(options, targetDistance);
        return jumpWanted;
    }

    void executeGotoRoute(GameOptions options, ClientPlayerEntity player, BlockPos waypoint,
                          double horizontalDistance) {
        clearDirectionalKeys(options);
        options.forwardKey.setPressed(true);
        updateGotoJump(options, player, waypoint);
        updateSprint(options, horizontalDistance);
    }

    void updateGotoJump(GameOptions options, ClientPlayerEntity player, BlockPos waypoint) {
        if (!canJump(player)) {
            options.jumpKey.setPressed(false);
            state.setFollowJump(false);
            return;
        }

        boolean targetAbove = waypoint.getY() - player.getY() >= GOTO_JUMP_TARGET_Y_DELTA
                && horizontalDistance(player, waypoint) <= FOLLOW_DISTANCE + 2.0;
        boolean blocked = player.horizontalCollision;

        if ((targetAbove || blocked) && jumpCooldownTicks <= 0) {
            options.jumpKey.setPressed(true);
            state.setFollowJump(true);
            jumpCooldownTicks = FOLLOW_JUMP_COOLDOWN_TICKS;
            return;
        }

        options.jumpKey.setPressed(false);
        state.setFollowJump(false);
    }

    void updateFollowRouteJump(GameOptions options, ClientPlayerEntity player, boolean jumpWanted) {
        if (!canJump(player)) {
            options.jumpKey.setPressed(false);
            state.setFollowJump(false);
            return;
        }

        if (jumpWanted && jumpCooldownTicks <= 0) {
            options.jumpKey.setPressed(true);
            state.setFollowJump(true);
            jumpCooldownTicks = FOLLOW_JUMP_COOLDOWN_TICKS;
            return;
        }

        options.jumpKey.setPressed(false);
        state.setFollowJump(false);
    }

    boolean followRouteJumpWanted(ClientPlayerEntity player, BlockPos waypoint) {
        return waypoint.getY() - player.getY() > GOTO_JUMP_TARGET_Y_DELTA || player.horizontalCollision;
    }

    boolean canJump(ClientPlayerEntity player) {
        return player.isOnGround()
                && !player.isTouchingWater()
                && !player.isSwimming()
                && !player.isClimbing();
    }

    private double horizontalDistance(ClientPlayerEntity player, BlockPos target) {
        double deltaX = target.getX() + 0.5 - player.getX();
        double deltaZ = target.getZ() + 0.5 - player.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private void clearDirectionalKeys(GameOptions options) {
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
    }
}
