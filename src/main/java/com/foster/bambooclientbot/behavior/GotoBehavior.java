package com.foster.bambooclientbot.behavior;

import com.foster.bambooclientbot.actions.ActionRequest;
import com.foster.bambooclientbot.actions.MovementDiagnostics;
import com.foster.bambooclientbot.actions.RouteExecutor;
import com.foster.bambooclientbot.control.LookController;
import com.foster.bambooclientbot.control.MovementController;
import com.foster.bambooclientbot.control.MovementRecovery;
import com.foster.bambooclientbot.navigation.PathPlanResult;
import com.foster.bambooclientbot.navigation.PathPlanner;
import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.GotoResult;
import com.foster.bambooclientbot.state.GotoTarget;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class GotoBehavior {
    private static final double GOTO_HORIZONTAL_ARRIVAL_DISTANCE = 1.5;
    private static final double GOTO_VERTICAL_ARRIVAL_DISTANCE = 2.0;
    private static final double GOTO_SEGMENT_ARRIVAL_DISTANCE = 1.5;
    private static final double GOTO_WAYPOINT_DISTANCE = 0.8;
    private static final double GOTO_FINAL_GAZE_DISTANCE = 4.0;
    private static final int ROUTE_STUCK_TICKS = 60;
    private static final int ROUTE_MAX_STUCK_REPLANS = 3;
    private static final int MAX_SEGMENT_PLAN_FAILURES = 3;
    private static final double SEGMENT_RANGE_MARGIN = 16.0;
    private static final double MIN_SEGMENT_DISTANCE = 16.0;
    private static final double SEGMENT_RETRY_STEP = 16.0;

    private final BotState state;
    private final PathPlanner pathPlanner;
    private final LookController lookController;
    private final RouteExecutor routeExecutor;
    private final MovementRecovery movementRecovery;
    private final MovementDiagnostics movementDiagnostics;
    private final MovementController movementController;
    private ActionRequest activeGoto;
    private List<BlockPos> activeGotoRoute = List.of();
    private int activeGotoRouteIndex;
    private int gotoStuckReplans;
    private boolean routeTransitionPending;
    private GotoTarget activeSegmentTarget;
    private boolean segmentedGoto;
    private boolean currentSegmentIntermediate;
    private int gotoSegmentIndex;
    private String lastSegmentFailureReason = "none";

    public GotoBehavior(BotState state, PathPlanner pathPlanner, LookController lookController,
                        RouteExecutor routeExecutor, MovementRecovery movementRecovery,
                        MovementDiagnostics movementDiagnostics, MovementController movementController) {
        this.state = state;
        this.pathPlanner = pathPlanner;
        this.lookController = lookController;
        this.routeExecutor = routeExecutor;
        this.movementRecovery = movementRecovery;
        this.movementDiagnostics = movementDiagnostics;
        this.movementController = movementController;
    }

    public void tick(MinecraftClient client, Runnable stopMovement) {
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
        updateActiveGotoState(player, target);

        if (horizontalDistance <= GOTO_HORIZONTAL_ARRIVAL_DISTANCE
                && verticalDifference <= GOTO_VERTICAL_ARRIVAL_DISTANCE) {
            stopMovement.run();
            activeGoto.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.setLastGotoResult(new GotoResult(target, "success", ""));
            state.clearActiveGoto();
            clearActiveRoute();
            resetSegmentState();
            lookController.clearNavigationGaze();
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

        if (segmentReached(player, activeSegmentTarget)) {
            if (!planNextSegment(client, player, target, stopMovement, false)) {
                return;
            }

            waypoint = currentWaypoint(player);
        }

        boolean routeTransition = routeTransitionPending;
        Vec3d steeringTarget = lookController.routeSteeringTarget(player, activeGotoRoute, activeGotoRouteIndex);
        boolean finalTargetGaze = horizontalDistance <= GOTO_FINAL_GAZE_DISTANCE;
        Vec3d gazeTarget = finalTargetGaze
                ? gotoGazeTarget(target)
                : lookController.routePreviewGazeTarget(player, activeGotoRoute, activeGotoRouteIndex);
        lookController.rotateForNavigation(
                player,
                steeringTarget,
                gazeTarget,
                finalTargetGaze ? "FINAL_TARGET" : "ROUTE_PREVIEW",
                activeGotoRouteIndex,
                activeGotoRoute.size(),
                false,
                routeTransition
        );
        routeTransitionPending = false;
        routeExecutor.tickJumpCooldown();
        RouteExecutor.RouteMovement movement = routeExecutor.gotoRouteMovement(player, waypoint, horizontalDistance,
                client.options.sprintKey.isPressed());
        movementController.applyRouteMovement(client.options, movement, "goto");
        movementDiagnostics.logMovement("goto", client.options, player, waypoint, waypoint.getY() - player.getY(),
                false, activeGotoRouteIndex, activeGotoRoute.size());
        movementDiagnostics.logMotionSpin("goto", client.options, player, waypoint, steeringTarget,
                lookController.lastNavigationMotion(), activeGotoRouteIndex, activeGotoRoute.size());
    }

    public void start(MinecraftClient client, ActionRequest request, Runnable beforeActivate, Runnable stopMovement) {
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
        resetSegmentState();
        resetStuckTracking();
        BlockPos start = client.player.getBlockPos();
        BlockPos targetPosition = BlockPos.ofFloored(target.x(), target.y(), target.z());
        PathPlanResult plan = initialPlan(client, start, targetPosition, target);

        if (!plan.found()) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastGotoResult(new GotoResult(target, "failed", lastSegmentFailureReason));
            state.clearActiveGoto();
            state.queueChatMessage("goto failed");
            resetSegmentState();
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
            resetSegmentState();
            return;
        }

        movementController.clearRouteMovement(client.options, "goto_start");
        state.setFollowJump(false);
        activatePlan(plan);
        lookController.rotateForNavigation(
                client.player,
                lookController.routeSteeringTarget(client.player, activeGotoRoute, activeGotoRouteIndex),
                lookController.routePreviewGazeTarget(client.player, activeGotoRoute, activeGotoRouteIndex),
                "ROUTE_PREVIEW",
                activeGotoRouteIndex,
                activeGotoRoute.size(),
                false,
                true
        );
        resetStuckTracking();
        activeGoto = request;
        updateActiveGotoState(client.player, target);
        state.queueChatMessage("going to " + target.formatForChat());
    }

    public void cancel(String reason) {
        if (activeGoto == null) {
            state.clearActiveGoto();
            routeTransitionPending = false;
            lookController.clearNavigationGaze();
            return;
        }

        activeGoto.setStatus(ActionRequest.ActionStatus.FAILED);
        state.setLastGotoResult(new GotoResult(activeGoto.gotoTarget(), "failed", reason));
        clearActiveRoute();
        resetSegmentState();
        lookController.clearNavigationGaze();
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

        PathPlanResult plan = planSegment(client, player.getBlockPos(), target, currentSegmentTargetPosition(target));

        if (!plan.found()) {
            lastSegmentFailureReason = plan.reason();
            return;
        }

        activatePlan(plan);
        resetStuckTracking();
        updateActiveGotoState(player, target);
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
        clearActiveRoute();
        resetSegmentState();
        lookController.clearNavigationGaze();
        resetStuckTracking();
    }

    private PathPlanResult initialPlan(MinecraftClient client, BlockPos start, BlockPos finalPosition, GotoTarget target) {
        PathPlanResult directPlan = pathPlanner.plan(client.world, start, finalPosition);

        if (directPlan.found()) {
            activeSegmentTarget = target;
            segmentedGoto = false;
            gotoSegmentIndex = 1;
            lastSegmentFailureReason = "none";
            return directPlan;
        }

        lastSegmentFailureReason = directPlan.reason();

        if (!"search_limit_reached".equals(directPlan.reason())) {
            lastSegmentFailureReason = "planner_failed";
            return PathPlanResult.notFound("planner_failed");
        }

        segmentedGoto = true;
        gotoSegmentIndex = 1;
        return planSegmentWithRetries(client, start, target, false);
    }

    private boolean planNextSegment(MinecraftClient client, ClientPlayerEntity player, GotoTarget target,
                                    Runnable stopMovement, boolean stuckReplan) {
        if (client.world == null || activeGoto == null) {
            fail(client, "movement_unavailable", stopMovement);
            return false;
        }

        PathPlanResult plan = planSegmentWithRetries(client, player.getBlockPos(), target, stuckReplan);

        if (!plan.found()) {
            fail(client, "segment_failed", stopMovement);
            return false;
        }

        if (!stuckReplan) {
            gotoSegmentIndex++;
        }

        activatePlan(plan);
        resetStuckTracking();
        updateActiveGotoState(player, target);
        return true;
    }

    private PathPlanResult planSegmentWithRetries(MinecraftClient client, BlockPos start, GotoTarget target,
                                                  boolean stuckReplan) {
        int segmentPlanFailures = 0;
        double maxSegmentDistance = Math.max(MIN_SEGMENT_DISTANCE,
                pathPlanner.maxDistanceFromStart() - SEGMENT_RANGE_MARGIN - (segmentPlanFailures * SEGMENT_RETRY_STEP));
        PathPlanResult lastPlan = PathPlanResult.notFound(lastSegmentFailureReason);

        while (segmentPlanFailures <= MAX_SEGMENT_PLAN_FAILURES && maxSegmentDistance >= MIN_SEGMENT_DISTANCE) {
            BlockPos segmentPosition = chooseSegmentTarget(start, target, maxSegmentDistance);
            PathPlanResult plan = planSegment(client, start, target, segmentPosition);

            if (plan.found()) {
                lastSegmentFailureReason = "none";
                return plan;
            }

            lastSegmentFailureReason = plan.reason();
            lastPlan = plan;
            segmentPlanFailures++;

            if (stuckReplan) {
                break;
            }

            maxSegmentDistance = Math.max(MIN_SEGMENT_DISTANCE, maxSegmentDistance - SEGMENT_RETRY_STEP);
        }

        return lastPlan.found() ? lastPlan : PathPlanResult.notFound("segment_failed");
    }

    private PathPlanResult planSegment(MinecraftClient client, BlockPos start, GotoTarget finalTarget,
                                       BlockPos segmentPosition) {
        BlockPos finalPosition = BlockPos.ofFloored(finalTarget.x(), finalTarget.y(), finalTarget.z());
        BlockPos planTarget = withinLocalPlanningRange(start, finalPosition) ? finalPosition : segmentPosition;
        PathPlanResult plan = pathPlanner.plan(client.world, start, planTarget);

        if (plan.found()) {
            activeSegmentTarget = toGotoTarget(planTarget);
            currentSegmentIntermediate = !planTarget.equals(finalPosition);
            segmentedGoto = segmentedGoto || currentSegmentIntermediate;
        }

        return plan;
    }

    private BlockPos chooseSegmentTarget(BlockPos start, GotoTarget target, double segmentDistance) {
        double targetX = target.x();
        double targetY = target.y();
        double targetZ = target.z();
        double deltaX = targetX - (start.getX() + 0.5);
        double deltaY = targetY - start.getY();
        double deltaZ = targetZ - (start.getZ() + 0.5);
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (horizontalDistance <= segmentDistance) {
            return BlockPos.ofFloored(targetX, targetY, targetZ);
        }

        double ratio = segmentDistance / horizontalDistance;
        double segmentX = start.getX() + 0.5 + (deltaX * ratio);
        double segmentZ = start.getZ() + 0.5 + (deltaZ * ratio);
        double maxVerticalStep = Math.max(1.0, pathPlanner.maxDistanceFromStart() - SEGMENT_RANGE_MARGIN);
        double segmentY = start.getY() + Math.max(-maxVerticalStep, Math.min(maxVerticalStep, deltaY * ratio));
        return BlockPos.ofFloored(segmentX, segmentY, segmentZ);
    }

    private boolean withinLocalPlanningRange(BlockPos start, BlockPos target) {
        int maxDistance = pathPlanner.maxDistanceFromStart();
        return Math.abs(target.getX() - start.getX()) <= maxDistance
                && Math.abs(target.getY() - start.getY()) <= maxDistance
                && Math.abs(target.getZ() - start.getZ()) <= maxDistance;
    }

    private boolean segmentReached(ClientPlayerEntity player, GotoTarget segmentTarget) {
        if (segmentTarget == null || !currentSegmentIntermediate) {
            return false;
        }

        double horizontalDistance = horizontalDistance(player, segmentTarget);
        double verticalDifference = Math.abs(player.getY() - segmentTarget.y());
        return horizontalDistance <= GOTO_SEGMENT_ARRIVAL_DISTANCE
                && verticalDifference <= GOTO_VERTICAL_ARRIVAL_DISTANCE;
    }

    private void activatePlan(PathPlanResult plan) {
        activeGotoRoute = plan.path();
        activeGotoRouteIndex = activeGotoRoute.size() > 1 ? 1 : 0;
        routeTransitionPending = true;
    }

    private BlockPos currentSegmentTargetPosition(GotoTarget finalTarget) {
        GotoTarget segmentTarget = activeSegmentTarget == null ? finalTarget : activeSegmentTarget;
        return BlockPos.ofFloored(segmentTarget.x(), segmentTarget.y(), segmentTarget.z());
    }

    private void updateActiveGotoState(ClientPlayerEntity player, GotoTarget target) {
        state.setActiveGoto(target, horizontalDistance(player, target), activeGotoRouteIndex, activeGotoRoute.size(),
                gotoStuckReplans, movementRecovery.gotoStuckTicks(), segmentedGoto, activeSegmentTarget,
                gotoSegmentIndex, lastSegmentFailureReason);
    }

    private void clearActiveRoute() {
        activeGoto = null;
        activeGotoRoute = List.of();
        activeGotoRouteIndex = 0;
        routeTransitionPending = false;
    }

    private void resetSegmentState() {
        activeSegmentTarget = null;
        segmentedGoto = false;
        currentSegmentIntermediate = false;
        gotoSegmentIndex = 0;
        lastSegmentFailureReason = "none";
    }

    private GotoTarget toGotoTarget(BlockPos position) {
        return new GotoTarget(position.getX(), position.getY(), position.getZ());
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

    private Vec3d gotoGazeTarget(GotoTarget target) {
        return new Vec3d(target.x(), target.y() + 1.5, target.z());
    }

    private double horizontalDistance(ClientPlayerEntity player, BlockPos target) {
        double deltaX = target.getX() + 0.5 - player.getX();
        double deltaZ = target.getZ() + 0.5 - player.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

}
