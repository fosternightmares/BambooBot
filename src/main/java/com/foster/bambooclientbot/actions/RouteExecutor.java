package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.network.ClientPlayerEntity;
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

    boolean pressJumpIfReady() {
        if (jumpCooldownTicks > 0) {
            return false;
        }

        state.setFollowJump(true);
        jumpCooldownTicks = FOLLOW_JUMP_COOLDOWN_TICKS;
        return true;
    }

    boolean sprintPressed(boolean currentSprintPressed, double targetDistance) {
        if (targetDistance > FOLLOW_SPRINT_ENABLE_DISTANCE) {
            return true;
        }

        if (targetDistance <= FOLLOW_SPRINT_DISABLE_DISTANCE) {
            return false;
        }

        return currentSprintPressed;
    }

    RouteMovement followRouteMovement(ClientPlayerEntity player, BlockPos waypoint, double targetDistance,
                                      boolean currentSprintPressed) {
        boolean jumpWanted = followRouteJumpWanted(player, waypoint);
        boolean jumpPressed = followRouteJumpPressed(player, jumpWanted);
        boolean sprintPressed = sprintPressed(currentSprintPressed, targetDistance);
        return new RouteMovement(jumpPressed, sprintPressed, jumpWanted);
    }

    RouteMovement gotoRouteMovement(ClientPlayerEntity player, BlockPos waypoint, double horizontalDistance,
                                    boolean currentSprintPressed) {
        boolean jumpPressed = gotoJumpPressed(player, waypoint);
        boolean sprintPressed = sprintPressed(currentSprintPressed, horizontalDistance);
        return new RouteMovement(jumpPressed, sprintPressed, false);
    }

    boolean gotoJumpPressed(ClientPlayerEntity player, BlockPos waypoint) {
        if (!canJump(player)) {
            state.setFollowJump(false);
            return false;
        }

        boolean targetAbove = waypoint.getY() - player.getY() >= GOTO_JUMP_TARGET_Y_DELTA
                && horizontalDistance(player, waypoint) <= FOLLOW_DISTANCE + 2.0;
        boolean blocked = player.horizontalCollision;

        if ((targetAbove || blocked) && jumpCooldownTicks <= 0) {
            state.setFollowJump(true);
            jumpCooldownTicks = FOLLOW_JUMP_COOLDOWN_TICKS;
            return true;
        }

        state.setFollowJump(false);
        return false;
    }

    boolean followRouteJumpPressed(ClientPlayerEntity player, boolean jumpWanted) {
        if (!canJump(player)) {
            state.setFollowJump(false);
            return false;
        }

        if (jumpWanted && jumpCooldownTicks <= 0) {
            state.setFollowJump(true);
            jumpCooldownTicks = FOLLOW_JUMP_COOLDOWN_TICKS;
            return true;
        }

        state.setFollowJump(false);
        return false;
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

    record RouteMovement(boolean jumpPressed, boolean sprintPressed, boolean jumpWanted) {
    }
}
