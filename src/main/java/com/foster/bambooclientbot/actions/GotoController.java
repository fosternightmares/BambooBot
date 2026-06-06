package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.navigation.PathPlanResult;
import com.foster.bambooclientbot.navigation.PathPlanner;
import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.GotoResult;
import com.foster.bambooclientbot.state.GotoTarget;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;

class GotoController {
    private static final double GOTO_HORIZONTAL_ARRIVAL_DISTANCE = 1.5;
    private static final double GOTO_VERTICAL_ARRIVAL_DISTANCE = 2.0;
    private static final double GOTO_WAYPOINT_DISTANCE = 0.8;
    private static final int ROUTE_STUCK_TICKS = 60;
    private static final int ROUTE_MAX_STUCK_REPLANS = 3;

    private final BotState state;
    private final PathPlanner pathPlanner;
    private final LookController lookController;
    private final RouteExecutor routeExecutor;
    private final MovementRecovery movementRecovery;
    private final MovementDiagnostics movementDiagnostics;
    private ActionRequest activeGoto;
    private List<BlockPos> activeGotoRoute = List.of();
    private int activeGotoRouteIndex;
    private int gotoStuckReplans;

    GotoController(BotState state, PathPlanner pathPlanner, LookController lookController,
                   RouteExecutor routeExecutor, MovementRecovery movementRecovery,
                   MovementDiagnostics movementDiagnostics) {
        this.state = state;
        this.pathPlanner = pathPlanner;
        this.lookController = lookController;
        this.routeExecutor = routeExecutor;
        this.movementRecovery = movementRecovery;
        this.movementDiagnostics = movementDiagnostics;
    }

    void tick(MinecraftClient client, Runnable stopMovement) {
        if (activeGoto == null) {
            return;
        }

        GotoTarget target = activeGoto.gotoTarget();

        if (client == null || client.player == null || client.options == null || target == null || activeGotoRoute.isEmpty()) {
            fail(client, "movement_unavailable", stopMovement);
            return;
        }

        ClientPlayerEntity player = client.player;
        double horizontalDistance = horizontalDistance(player, target);
        double verticalDifference = Math.abs(player.getY() - target.y());
        state.setActiveGoto(target, horizontalDistance, activeGotoRouteIndex, activeGotoRoute.size(), gotoStuckReplans,
                movementRecovery.gotoStuckTicks());

        if (horizontalDistance <= GOTO_HORIZONTAL_ARRIVAL_DISTANCE
                && verticalDifference <= GOTO_VERTICAL_ARRIVAL_DISTANCE) {
            stopMovement.run();
            activeGoto.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.setLastGotoResult(new GotoResult(target, "success", ""));
            state.clearActiveGoto();
            activeGoto = null;
            activeGotoRoute = List.of();
            activeGotoRouteIndex = 0;
            return;
        }

        int previousRouteIndex = activeGotoRouteIndex;
        BlockPos waypoint = currentWaypoint(player);
        if (activeGotoRouteIndex != previousRouteIndex) {
            resetStuckTracking();
        }

        double waypointDistance = horizontalDistance(player, waypoint);
        if (isRouteStuck(client, player, target, waypointDistance, stopMovement)) {
            return;
        }

        lookController.rotateToward(player, lookController.routeLookTarget(player, activeGotoRoute, activeGotoRouteIndex));
        routeExecutor.tickJumpCooldown();
        routeExecutor.executeGotoRoute(client.options, player, waypoint, horizontalDistance);
        movementDiagnostics.logMovement("goto", client.options, player, waypoint, waypoint.getY() - player.getY(),
                false, activeGotoRouteIndex, activeGotoRoute.size());
    }

    void start(MinecraftClient client, ActionRequest request, Runnable beforeActivate, Runnable stopMovement) {
        GotoTarget target = request.gotoTarget();

        if (client == null || client.player == null || client.world == null || client.options == null || target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastGotoResult(new GotoResult(target, "failed", "movement_unavailable"));
            state.clearActiveGoto();
            gotoStuckReplans = 0;
            resetStuckTracking();
            state.queueChatMessage("goto failed");
            return;
        }

        cancel("goto_cancelled");
        gotoStuckReplans = 0;
        resetStuckTracking();
        BlockPos start = client.player.getBlockPos();
        BlockPos targetPosition = BlockPos.ofFloored(target.x(), target.y(), target.z());
        PathPlanResult plan = pathPlanner.plan(client.world, start, targetPosition);

        if (!plan.found()) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastGotoResult(new GotoResult(target, "failed", plan.reason()));
            state.clearActiveGoto();
            state.queueChatMessage("goto failed");
            return;
        }

        beforeActivate.run();
        routeExecutor.resetJumpCooldown();

        double horizontalDistance = horizontalDistance(client.player, target);
        double verticalDifference = Math.abs(client.player.getY() - target.y());

        if (horizontalDistance <= GOTO_HORIZONTAL_ARRIVAL_DISTANCE
                && verticalDifference <= GOTO_VERTICAL_ARRIVAL_DISTANCE) {
            stopMovement.run();
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.setLastGotoResult(new GotoResult(target, "success", ""));
            state.clearActiveGoto();
            state.queueChatMessage("arrived");
            return;
        }

        clearDirectionalKeys(client.options);
        client.options.jumpKey.setPressed(false);
        state.setFollowJump(false);
        activeGotoRoute = plan.path();
        activeGotoRouteIndex = activeGotoRoute.size() > 1 ? 1 : 0;
        lookController.rotateToward(client.player, lookController.routeLookTarget(client.player, activeGotoRoute, activeGotoRouteIndex));
        resetStuckTracking();
        state.setActiveGoto(target, horizontalDistance, activeGotoRouteIndex, activeGotoRoute.size(), gotoStuckReplans,
                movementRecovery.gotoStuckTicks());
        activeGoto = request;
        state.queueChatMessage("going to " + target.formatForChat());
    }

    void cancel(String reason) {
        if (activeGoto == null) {
            state.clearActiveGoto();
            return;
        }

        activeGoto.setStatus(ActionRequest.ActionStatus.FAILED);
        state.setLastGotoResult(new GotoResult(activeGoto.gotoTarget(), "failed", reason));
        activeGoto = null;
        activeGotoRoute = List.of();
        activeGotoRouteIndex = 0;
        resetStuckTracking();
        state.clearActiveGoto();
    }

    private boolean isRouteStuck(MinecraftClient client, ClientPlayerEntity player, GotoTarget target,
                                 double waypointDistance, Runnable stopMovement) {
        if (state.followJump()) {
            return false;
        }

        if (movementRecovery.recordRouteProgress(waypointDistance, false)) {
            return false;
        }

        if (movementRecovery.gotoStuckTicks() < ROUTE_STUCK_TICKS) {
            return false;
        }

        gotoStuckReplans++;

        if (gotoStuckReplans > ROUTE_MAX_STUCK_REPLANS) {
            fail(client, "stuck", stopMovement);
            return true;
        }

        resetStuckTracking();
        replanRoute(client, player, target, stopMovement);
        return true;
    }

    private void replanRoute(MinecraftClient client, ClientPlayerEntity player, GotoTarget target,
                             Runnable stopMovement) {
        if (client.world == null || activeGoto == null) {
            fail(client, "movement_unavailable", stopMovement);
            return;
        }

        BlockPos targetPosition = BlockPos.ofFloored(target.x(), target.y(), target.z());
        PathPlanResult plan = pathPlanner.plan(client.world, player.getBlockPos(), targetPosition);

        if (!plan.found()) {
            return;
        }

        activeGotoRoute = plan.path();
        activeGotoRouteIndex = activeGotoRoute.size() > 1 ? 1 : 0;
        resetStuckTracking();
        state.setActiveGoto(
                target,
                horizontalDistance(player, target),
                activeGotoRouteIndex,
                activeGotoRoute.size(),
                gotoStuckReplans,
                movementRecovery.gotoStuckTicks()
        );
    }

    private void fail(MinecraftClient client, String reason, Runnable stopMovement) {
        GotoTarget target = activeGoto == null ? null : activeGoto.gotoTarget();

        if (activeGoto != null) {
            activeGoto.setStatus(ActionRequest.ActionStatus.FAILED);
        }

        stopMovement.run();
        if ("stuck".equals(reason)) {
            state.setLastGotoResult(new GotoResult(target, "stuck", ""));
        } else {
            state.setLastGotoResult(new GotoResult(target, "failed", reason));
        }
        state.clearActiveGoto();
        activeGoto = null;
        activeGotoRoute = List.of();
        activeGotoRouteIndex = 0;
        resetStuckTracking();
    }

    private BlockPos currentWaypoint(ClientPlayerEntity player) {
        activeGotoRouteIndex = routeIndexFor(player, activeGotoRoute, activeGotoRouteIndex);
        return activeGotoRoute.get(activeGotoRouteIndex);
    }

    private int routeIndexFor(ClientPlayerEntity player, List<BlockPos> route, int currentIndex) {
        int routeIndex = currentIndex;

        while (routeIndex < route.size() - 1
                && horizontalDistance(player, route.get(routeIndex)) <= GOTO_WAYPOINT_DISTANCE) {
            routeIndex++;
        }

        return routeIndex;
    }

    private void resetStuckTracking() {
        movementRecovery.resetGotoStuckTracking();
    }

    private double horizontalDistance(ClientPlayerEntity player, GotoTarget target) {
        double deltaX = target.x() - player.getX();
        double deltaZ = target.z() - player.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
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
