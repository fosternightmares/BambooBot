package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.navigation.PathPlanResult;
import com.foster.bambooclientbot.navigation.PathPlanner;
import com.foster.bambooclientbot.state.BotState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

class FollowController {
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double FOLLOW_SPRINT_ENABLE_DISTANCE = 6.0;
    private static final boolean FOLLOW_DIRECT_CHASE_ENABLED = false;
    private static final double FOLLOW_DIRECT_CHASE_DISTANCE = 12.0;
    private static final int FOLLOW_PREDICTION_TICKS = 10;
    private static final double ROUTE_LOOK_HEIGHT = 1.5;
    private static final int ROUTE_STUCK_TICKS = 60;
    private static final int ROUTE_MAX_STUCK_REPLANS = 3;
    private static final double FOLLOW_REPLAN_DISTANCE = 1.5;
    private static final double FOLLOW_WAYPOINT_DISTANCE = 1.0;
    private static final int FOLLOW_GOAL_VERTICAL_SEARCH = 1;
    private static final int FOLLOW_REPLAN_TICKS = 10;

    private final BotState state;
    private final PathPlanner pathPlanner;
    private final LookController lookController;
    private final RouteExecutor routeExecutor;
    private final MovementRecovery movementRecovery;
    private final SimpleMovementController simpleMovementController;
    private final MovementDiagnostics movementDiagnostics;
    private ActionRequest activeFollow;
    private int followStuckReplans;
    private List<BlockPos> route = List.of();
    private int routeIndex;
    private FollowGoal goal;
    private int replanTicks;
    private boolean plannedSuccessfully;

    FollowController(BotState state, PathPlanner pathPlanner, LookController lookController,
                     RouteExecutor routeExecutor, MovementRecovery movementRecovery,
                     SimpleMovementController simpleMovementController, MovementDiagnostics movementDiagnostics) {
        this.state = state;
        this.pathPlanner = pathPlanner;
        this.lookController = lookController;
        this.routeExecutor = routeExecutor;
        this.movementRecovery = movementRecovery;
        this.simpleMovementController = simpleMovementController;
        this.movementDiagnostics = movementDiagnostics;
    }

    void tick(MinecraftClient client, Consumer<MinecraftClient> stopMovement,
              BiFunction<ClientPlayerEntity, String, AbstractClientPlayerEntity> targetFinder) {
        if (activeFollow == null) {
            return;
        }

        if (client == null || client.player == null || client.world == null || client.options == null) {
            String targetName = activeFollow.actionData();
            int replans = followStuckReplans;
            int stuckTicks = movementRecovery.followStuckTicks();
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            clearActiveFollowRouteLocal();
            state.setActiveFollowRoute(targetName, 0, 0, "movement_unavailable", replans, stuckTicks);
            stopMovement.accept(client);
            state.setFollowJump(false);
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = targetFinder.apply(player, activeFollow.actionData());

        if (target == null) {
            String targetName = activeFollow.actionData();
            int replans = followStuckReplans;
            int stuckTicks = movementRecovery.followStuckTicks();
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            clearActiveFollowRouteLocal();
            state.setActiveFollowRoute(targetName, 0, 0, "target_player_not_found", replans, stuckTicks);
            stopMovement.accept(client);
            state.setFollowJump(false);
            return;
        }

        double followDistanceToTarget = player.distanceTo(target);
        if (followDistanceToTarget <= FOLLOW_DISTANCE) {
            logFollowDiagnostics(player, target, true);
            simpleMovementController.clearDirectionalKeys(client.options);
            client.options.jumpKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
            state.setFollowJump(false);
            clearActiveFollowRoute();
            return;
        }

        routeExecutor.tickJumpCooldown();

        if (FOLLOW_DIRECT_CHASE_ENABLED && canDirectChase(player, target)) {
            clearActiveFollowRouteLocal();
            chaseFollowTarget(client.options, player, target);
            state.setActiveFollowRoute(
                    activeFollow.actionData(),
                    0,
                    0,
                    "success",
                    followStuckReplans,
                    movementRecovery.followStuckTicks()
            );
            return;
        }

        tickReplan(client, player, target, state, activeFollow.actionData(), followStuckReplans,
                movementRecovery.followStuckTicks(), movementDiagnostics);
        if (consumePlannedSuccessfully()) {
            movementRecovery.resetFollowRecovery();
            movementDiagnostics.setFollowRecovery("none");
            resetFollowStuckTracking();
        }

        if (isRouteEmpty()) {
            if (movementRecovery.tickFollowEmptyRouteRecovery(
                    client,
                    player,
                    target,
                    movementDiagnostics,
                    () -> planRoute(client, player, target, state, activeFollow.actionData(),
                            followStuckReplans, movementRecovery.followStuckTicks(), movementDiagnostics),
                    goalLabel(),
                    routeIndex(),
                    routeLength(),
                    FOLLOW_DISTANCE
            )) {
                return;
            }

            logFollowDiagnostics(player, target, false);
            simpleMovementController.clearDirectionalKeys(client.options);
            client.options.jumpKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
            state.setFollowJump(false);
            return;
        }

        if (advanceRouteIndex(player)) {
            resetFollowStuckTracking();
        }

        BlockPos waypoint = waypoint();
        double waypointDistance = horizontalDistance(player, waypoint);
        if (isFollowRouteStuck(client, player, target, waypointDistance, stopMovement)) {
            return;
        }

        Vec3d gazeTarget = player.canSee(target)
                ? lookController.upperBodyTarget(target)
                : lookController.routePreviewGazeTarget(player, route(), routeIndex());
        lookController.rotateForNavigation(player, lookController.routeSteeringTarget(player, route(), routeIndex()), gazeTarget);
        boolean jumpWanted = routeExecutor.executeFollowRoute(client.options, player, waypoint, player.distanceTo(target));
        movementDiagnostics.logMovement("follow_route", client.options, player, waypoint, waypoint.getY() - player.getY(),
                jumpWanted,
                routeIndex(),
                routeLength());
        logFollowDiagnostics(player, target, false);
        state.setActiveFollowRoute(
                activeFollow.actionData(),
                routeIndex(),
                routeLength(),
                "success",
                followStuckReplans,
                movementRecovery.followStuckTicks()
        );
    }

    void startFollow(MinecraftClient client, ActionRequest request, Runnable cancelConflictingMovement,
                     BiFunction<ClientPlayerEntity, String, AbstractClientPlayerEntity> targetFinder) {
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

        cancelConflictingMovement.run();
        clearActiveFollowRoute();
        routeExecutor.resetJumpCooldown();
        simpleMovementController.clearDirectionalKeys(client.options);
        client.options.sprintKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        state.setFollowJump(false);
        activeFollow = request;
        start(client, player, target, activeFollow.actionData(), followStuckReplans,
                movementRecovery.followStuckTicks());
        if (consumePlannedSuccessfully()) {
            movementRecovery.resetFollowRecovery();
            movementDiagnostics.setFollowRecovery("none");
            resetFollowStuckTracking();
        }
        state.queueChatMessage("following " + request.actionData());
    }

    void cancelActiveFollow() {
        activeFollow = null;
        clearActiveFollowRoute();
    }

    void start(MinecraftClient client, ClientPlayerEntity player, AbstractClientPlayerEntity target,
               String targetName, int replans, int stuckTicks) {
        replanTicks = 0;
        movementDiagnostics.setFollowReplanReason("start");
        planRoute(client, player, target, state, targetName, replans, stuckTicks, movementDiagnostics);
    }

    void tickReplan(MinecraftClient client, ClientPlayerEntity player, AbstractClientPlayerEntity target,
                    BotState state, String targetName, int replans, int stuckTicks,
                    MovementDiagnostics diagnostics) {
        if (replanTicks > 0) {
            replanTicks--;
        }

        boolean targetMoved = goal == null || goal.targetMovedMoreThan(target, FOLLOW_REPLAN_DISTANCE);

        if (route.isEmpty() || targetMoved || replanTicks <= 0) {
            diagnostics.setFollowReplanReason(diagnostics.followReplanReason(route.isEmpty(), targetMoved, replanTicks <= 0));
            planRoute(client, player, target, state, targetName, replans, stuckTicks, diagnostics);
        }
    }

    void planRoute(MinecraftClient client, ClientPlayerEntity player, AbstractClientPlayerEntity target,
                   BotState state, String targetName, int replans, int stuckTicks,
                   MovementDiagnostics diagnostics) {
        if (client.world == null) {
            diagnostics.setFollowReplanReason("movement_unavailable");
            clear(state);
            return;
        }

        FollowPlan followPlan = planFollowGoal(client, player, target, diagnostics);
        replanTicks = FOLLOW_REPLAN_TICKS;

        if (!followPlan.plan().found()) {
            route = List.of();
            routeIndex = 0;
            goal = followPlan.goal();
            plannedSuccessfully = false;
            state.setActiveFollowRoute(targetName, 0, 0, followPlan.plan().reason(), replans, stuckTicks);
            return;
        }

        route = followPlan.plan().path();
        routeIndex = route.size() > 1 ? 1 : 0;
        goal = followPlan.goal();
        plannedSuccessfully = true;
        state.setActiveFollowRoute(targetName, routeIndex, route.size(), "success", replans, stuckTicks);
    }

    boolean consumePlannedSuccessfully() {
        boolean result = plannedSuccessfully;
        plannedSuccessfully = false;
        return result;
    }

    boolean isRouteEmpty() {
        return route.isEmpty();
    }

    BlockPos waypoint() {
        return route.get(routeIndex);
    }

    int routeIndex() {
        return routeIndex;
    }

    int routeLength() {
        return route.size();
    }

    List<BlockPos> route() {
        return route;
    }

    boolean advanceRouteIndex(ClientPlayerEntity player) {
        int previousRouteIndex = routeIndex;

        while (routeIndex < route.size() - 1
                && horizontalDistance(player, route.get(routeIndex)) <= FOLLOW_WAYPOINT_DISTANCE) {
            routeIndex++;
        }

        return routeIndex != previousRouteIndex;
    }

    void clear(BotState state) {
        clearLocal();
        state.clearActiveFollowRoute();
    }

    void clearLocal() {
        route = List.of();
        routeIndex = 0;
        goal = null;
        replanTicks = 0;
        plannedSuccessfully = false;
    }

    String goalLabel() {
        return goal == null ? "none" : goalLabel(goal.routeGoal());
    }

    private void clearActiveFollowRoute() {
        clear(state);
        resetFollowState();
    }

    private void clearActiveFollowRouteLocal() {
        clearLocal();
        resetFollowState();
    }

    private void resetFollowState() {
        movementRecovery.resetFollowStuckTracking();
        followStuckReplans = 0;
        movementRecovery.resetFollowRecovery();
        movementDiagnostics.clearFollowState();
    }

    private BlockPos predictedFollowTarget(AbstractClientPlayerEntity target) {
        Vec3d velocity = target.getVelocity();
        Vec3d predicted = new Vec3d(target.getX(), target.getY(), target.getZ()).add(velocity.multiply(FOLLOW_PREDICTION_TICKS));
        return BlockPos.ofFloored(predicted.x, predicted.y, predicted.z);
    }

    private boolean canDirectChase(ClientPlayerEntity player, AbstractClientPlayerEntity target) {
        return Math.abs(target.getY() - player.getY()) <= 0.5
                && player.distanceTo(target) <= FOLLOW_DIRECT_CHASE_DISTANCE
                && player.canSee(target);
    }

    private void chaseFollowTarget(GameOptions options, ClientPlayerEntity player, AbstractClientPlayerEntity target) {
        Vec3d predicted = new Vec3d(target.getX(), target.getY(), target.getZ()).add(target.getVelocity().multiply(FOLLOW_PREDICTION_TICKS));
        BlockPos waypoint = BlockPos.ofFloored(predicted.x, target.getY(), predicted.z);
        Vec3d lookTarget = new Vec3d(predicted.x, Math.max(player.getEyeY() - 0.2, target.getY() + ROUTE_LOOK_HEIGHT), predicted.z);

        lookController.rotateToward(player, lookTarget);
        simpleMovementController.clearDirectionalKeys(options);
        options.forwardKey.setPressed(true);
        routeExecutor.updateSprint(options, player.distanceTo(target));
        updateFollowDirectJump(options, player, target);
        movementDiagnostics.logMovement("follow_direct", options, player, waypoint, target.getY() - player.getY(), false, 0, 0);
    }

    private void updateFollowDirectJump(GameOptions options, ClientPlayerEntity player, AbstractClientPlayerEntity target) {
        boolean flatGroundChase = Math.abs(target.getY() - player.getY()) < 0.5;

        if (!routeExecutor.canJump(player) || !flatGroundChase || player.distanceTo(target) <= FOLLOW_SPRINT_ENABLE_DISTANCE) {
            options.jumpKey.setPressed(false);
            state.setFollowJump(false);
            return;
        }

        if (routeExecutor.pressJumpIfReady(options)) {
            return;
        }

        options.jumpKey.setPressed(false);
        state.setFollowJump(false);
    }

    private boolean isFollowRouteStuck(MinecraftClient client, ClientPlayerEntity player, AbstractClientPlayerEntity target,
                                       double waypointDistance, Consumer<MinecraftClient> stopMovement) {
        if (state.followJump()) {
            return false;
        }

        if (movementRecovery.recordRouteProgress(waypointDistance, true)) {
            return false;
        }

        if (movementRecovery.followStuckTicks() < ROUTE_STUCK_TICKS) {
            return false;
        }

        followStuckReplans++;

        if (followStuckReplans > ROUTE_MAX_STUCK_REPLANS) {
            String targetName = activeFollow.actionData();
            int finalReplans = followStuckReplans;
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            clearActiveFollowRouteLocal();
            state.setActiveFollowRoute(targetName, 0, 0, "stuck", finalReplans, ROUTE_STUCK_TICKS);
            stopMovement.accept(client);
            return true;
        }

        resetFollowStuckTracking();
        planRoute(client, player, target, state, activeFollow.actionData(), followStuckReplans,
                movementRecovery.followStuckTicks(), movementDiagnostics);
        if (consumePlannedSuccessfully()) {
            movementRecovery.resetFollowRecovery();
            movementDiagnostics.setFollowRecovery("none");
            resetFollowStuckTracking();
        }
        return true;
    }

    private void resetFollowStuckTracking() {
        movementRecovery.resetFollowStuckTracking();
    }

    private void logFollowDiagnostics(ClientPlayerEntity player, AbstractClientPlayerEntity target,
                                      boolean followGoalSatisfied) {
        movementDiagnostics.logFollow(
                player,
                target,
                followGoalSatisfied,
                goalLabel(),
                routeIndex(),
                routeLength(),
                FOLLOW_DISTANCE,
                movementRecovery.followRecoveryActive()
        );
    }

    private FollowPlan planFollowGoal(MinecraftClient client, ClientPlayerEntity player,
                                      AbstractClientPlayerEntity target, MovementDiagnostics diagnostics) {
        FollowGoal fallbackGoal = new FollowGoal(target, entityPosition(target), target.getBlockPos());
        PathPlanResult bestPlan = null;
        FollowGoal bestGoal = fallbackGoal;
        List<BlockPos> candidates = followGoalCandidates(target);
        int successfulCandidates = 0;
        boolean failedBeforeSuccess = false;
        boolean bestUsedFallback = false;

        for (BlockPos candidate : candidates) {
            PathPlanResult plan = pathPlanner.plan(client.world, player.getBlockPos(), candidate);
            if (!plan.found() || plan.path().isEmpty()) {
                failedBeforeSuccess = true;
                continue;
            }

            successfulCandidates++;
            if (bestPlan == null || plan.length() < bestPlan.length()) {
                bestPlan = plan;
                bestGoal = new FollowGoal(target, entityPosition(target), candidate);
                bestUsedFallback = failedBeforeSuccess;
            }
        }

        diagnostics.recordFollowPlanStats(new PlanStats(
                candidates.size(),
                successfulCandidates,
                bestPlan == null ? "none" : goalLabel(bestGoal.routeGoal()),
                bestPlan != null && bestUsedFallback
        ));

        if (bestPlan == null) {
            return new FollowPlan(fallbackGoal, PathPlanResult.notFound("path_not_found"));
        }

        return new FollowPlan(bestGoal, bestPlan);
    }

    private List<BlockPos> followGoalCandidates(AbstractClientPlayerEntity target) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos targetBlock = target.getBlockPos();
        int radiusLimit = (int) Math.ceil(FOLLOW_DISTANCE);

        addFollowGoalCandidatesAtY(candidates, targetBlock, radiusLimit, 0);

        for (int deltaY = 1; deltaY <= FOLLOW_GOAL_VERTICAL_SEARCH; deltaY++) {
            addFollowGoalCandidatesAtY(candidates, targetBlock, radiusLimit, deltaY);
        }

        for (int deltaY = -1; deltaY >= -FOLLOW_GOAL_VERTICAL_SEARCH; deltaY--) {
            addFollowGoalCandidatesAtY(candidates, targetBlock, radiusLimit, deltaY);
        }

        return candidates;
    }

    private void addFollowGoalCandidatesAtY(List<BlockPos> candidates, BlockPos targetBlock, int radiusLimit, int deltaY) {
        for (int radius = 1; radius <= radiusLimit; radius++) {
            for (int deltaX = -radius; deltaX <= radius; deltaX++) {
                for (int deltaZ = -radius; deltaZ <= radius; deltaZ++) {
                    if (Math.max(Math.abs(deltaX), Math.abs(deltaZ)) != radius) {
                        continue;
                    }

                    double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    if (horizontalDistance > FOLLOW_DISTANCE) {
                        continue;
                    }

                    candidates.add(targetBlock.add(deltaX, deltaY, deltaZ));
                }
            }
        }
    }

    private Vec3d entityPosition(AbstractClientPlayerEntity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private double horizontalDistance(ClientPlayerEntity player, BlockPos target) {
        double deltaX = target.getX() + 0.5 - player.getX();
        double deltaZ = target.getZ() + 0.5 - player.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private String goalLabel(BlockPos goal) {
        if (goal == null) {
            return "none";
        }

        return goal.getX() + "," + goal.getY() + "," + goal.getZ();
    }

    record PlanStats(int candidateCount, int successfulCandidates, String selectedCandidate,
                     boolean emptyRouteFallback) {
    }

    private record FollowGoal(AbstractClientPlayerEntity target, Vec3d targetPosition, BlockPos routeGoal) {
        private boolean targetMovedMoreThan(AbstractClientPlayerEntity currentTarget, double distance) {
            return currentTarget == null
                    || currentTarget != target
                    || new Vec3d(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ()).squaredDistanceTo(targetPosition)
                    > distance * distance;
        }
    }

    private record FollowPlan(FollowGoal goal, PathPlanResult plan) {
    }
}
