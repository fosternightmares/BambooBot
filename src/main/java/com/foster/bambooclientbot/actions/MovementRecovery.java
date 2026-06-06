package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;

class MovementRecovery {
    private static final int FOLLOW_EMPTY_ROUTE_RECOVERY_TICKS = 3;
    private static final int FOLLOW_FORWARD_NUDGE_TICKS = 8;
    private static final double ROUTE_PROGRESS_EPSILON = 0.15;

    private final BotState state;
    private final LookController lookController;
    private double bestFollowWaypointDistance = Double.POSITIVE_INFINITY;
    private int followStuckTicks;
    private double bestGotoWaypointDistance = Double.POSITIVE_INFINITY;
    private int gotoStuckTicks;
    private int followEmptyRouteTicks;
    private int followForwardNudgeTicks;

    MovementRecovery(BotState state, LookController lookController) {
        this.state = state;
        this.lookController = lookController;
    }

    boolean tickFollowEmptyRouteRecovery(MinecraftClient client, ClientPlayerEntity player,
                                         AbstractClientPlayerEntity target, MovementDiagnostics diagnostics,
                                         Runnable retryRoutePlanning, String goalLabel, int routeIndex,
                                         int routeLength, double followDistance) {
        if (followForwardNudgeTicks > 0) {
            applyFollowForwardNudge(client, player, target, diagnostics, goalLabel, routeIndex, routeLength,
                    followDistance);
            followForwardNudgeTicks--;

            if (followForwardNudgeTicks <= 0) {
                diagnostics.setFollowReplanReason("forward_nudge_retry");
                retryRoutePlanning.run();
            }

            return true;
        }

        if (diagnostics.followCandidateCount() > 0 && diagnostics.followSuccessfulCandidates() == 0) {
            followEmptyRouteTicks++;
        } else {
            followEmptyRouteTicks = 0;
        }

        if (followEmptyRouteTicks < FOLLOW_EMPTY_ROUTE_RECOVERY_TICKS) {
            return false;
        }

        followEmptyRouteTicks = 0;
        followForwardNudgeTicks = FOLLOW_FORWARD_NUDGE_TICKS;
        diagnostics.setFollowRecovery("forward_nudge");
        diagnostics.forceNextFollowLog();
        applyFollowForwardNudge(client, player, target, diagnostics, goalLabel, routeIndex, routeLength,
                followDistance);
        followForwardNudgeTicks--;
        return true;
    }

    void resetFollowRecovery() {
        followEmptyRouteTicks = 0;
        followForwardNudgeTicks = 0;
    }

    boolean followRecoveryActive() {
        return followForwardNudgeTicks > 0;
    }

    boolean recordRouteProgress(double waypointDistance, boolean followRoute) {
        if (followRoute) {
            if (waypointDistance + ROUTE_PROGRESS_EPSILON < bestFollowWaypointDistance) {
                bestFollowWaypointDistance = waypointDistance;
                followStuckTicks = 0;
                return true;
            }

            followStuckTicks++;
            return false;
        }

        if (waypointDistance + ROUTE_PROGRESS_EPSILON < bestGotoWaypointDistance) {
            bestGotoWaypointDistance = waypointDistance;
            gotoStuckTicks = 0;
            return true;
        }

        gotoStuckTicks++;
        return false;
    }

    void resetFollowStuckTracking() {
        bestFollowWaypointDistance = Double.POSITIVE_INFINITY;
        followStuckTicks = 0;
    }

    void resetGotoStuckTracking() {
        bestGotoWaypointDistance = Double.POSITIVE_INFINITY;
        gotoStuckTicks = 0;
    }

    int followStuckTicks() {
        return followStuckTicks;
    }

    int gotoStuckTicks() {
        return gotoStuckTicks;
    }

    private void applyFollowForwardNudge(MinecraftClient client, ClientPlayerEntity player,
                                         AbstractClientPlayerEntity target, MovementDiagnostics diagnostics,
                                         String goalLabel, int routeIndex, int routeLength, double followDistance) {
        lookController.rotateToward(player, target.getEyePos());
        clearDirectionalKeys(client.options);
        client.options.forwardKey.setPressed(true);
        client.options.jumpKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        state.setFollowJump(false);
        diagnostics.logFollow(player, target, false, goalLabel, routeIndex, routeLength, followDistance,
                followRecoveryActive());
    }

    private void clearDirectionalKeys(net.minecraft.client.option.GameOptions options) {
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
    }
}
