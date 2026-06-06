package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.navigation.PathPlanResult;
import com.foster.bambooclientbot.navigation.PathPlanner;
import com.foster.bambooclientbot.state.BotState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

class FollowController {
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double FOLLOW_REPLAN_DISTANCE = 1.5;
    private static final double FOLLOW_WAYPOINT_DISTANCE = 1.0;
    private static final int FOLLOW_GOAL_VERTICAL_SEARCH = 1;
    private static final int FOLLOW_REPLAN_TICKS = 10;

    private final PathPlanner pathPlanner;
    private List<BlockPos> route = List.of();
    private int routeIndex;
    private FollowGoal goal;
    private int replanTicks;
    private boolean plannedSuccessfully;

    FollowController(PathPlanner pathPlanner) {
        this.pathPlanner = pathPlanner;
    }

    void start(MinecraftClient client, ClientPlayerEntity player, AbstractClientPlayerEntity target,
               BotState state, String targetName, int replans, int stuckTicks, MovementDiagnostics diagnostics) {
        replanTicks = 0;
        diagnostics.setFollowReplanReason("start");
        planRoute(client, player, target, state, targetName, replans, stuckTicks, diagnostics);
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
