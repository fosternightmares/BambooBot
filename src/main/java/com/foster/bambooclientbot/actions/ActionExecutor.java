package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.navigation.NavigationGrid;
import com.foster.bambooclientbot.navigation.PathPlanner;
import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerSnapshot;
import com.foster.bambooclientbot.state.LookedAtBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ActionExecutor {
    private static final double LOOK_AT_RANGE = 32.0;
    private static final double APPROACH_TARGET_DISTANCE = 2.5;
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double FOLLOW_SPRINT_ENABLE_DISTANCE = 6.0;
    private static final boolean FOLLOW_DIRECT_CHASE_ENABLED = false;
    private static final double FOLLOW_DIRECT_CHASE_DISTANCE = 12.0;
    private static final int FOLLOW_PREDICTION_TICKS = 10;
    private static final double ROUTE_LOOK_HEIGHT = 1.5;
    private static final int ROUTE_STUCK_TICKS = 60;
    private static final int ROUTE_MAX_STUCK_REPLANS = 3;
    private static final long AUTOSWING_INTERVAL_MILLIS = 1_000L;

    private final BotState state;
    private final PathPlanner pathPlanner = new PathPlanner(new NavigationGrid());
    private final LookController lookController = new LookController();
    private final MovementDiagnostics movementDiagnostics = new MovementDiagnostics();
    private final FollowController followController = new FollowController(pathPlanner);
    private final RouteExecutor routeExecutor;
    private final MovementRecovery movementRecovery;
    private final InventoryActionExecutor inventoryActionExecutor;
    private final GotoController gotoController;
    private ActionRequest timedMovement;
    private int timedMovementTicksRemaining;
    private ActionRequest activeApproach;
    private ActionRequest activeFollow;
    private int followStuckReplans;

    public ActionExecutor(BotState state) {
        this.state = state;
        routeExecutor = new RouteExecutor(state);
        movementRecovery = new MovementRecovery(state, lookController);
        inventoryActionExecutor = new InventoryActionExecutor(state, lookController);
        gotoController = new GotoController(state, pathPlanner, lookController, routeExecutor, movementRecovery,
                movementDiagnostics);
    }

    public void tick(MinecraftClient client) {
        ActionRequest request = state.pollAction();

        if (request != null) {
            executeAction(client, request);
            tickAutoSneak(client);
            tickAutoUse(client);
            tickAutoSwing(client);
            return;
        }

        tickApproach(client);
        tickFollow(client);
        gotoController.tick(client, () -> stop(client));
        tickTimedMovement(client);
        tickAutoSneak(client);
        tickAutoUse(client);
        tickAutoSwing(client);
    }

    private void executeAction(MinecraftClient client, ActionRequest request) {
        request.setStatus(ActionRequest.ActionStatus.RUNNING);
        state.setActiveAction(request.actionType().name());

        try {
            if (request.actionType() == ActionRequest.ActionType.STOP) {
                activeApproach = null;
                activeFollow = null;
                clearActiveFollowRoute();
                gotoController.cancel("goto_cancelled");
                timedMovement = null;
                timedMovementTicksRemaining = 0;
                routeExecutor.resetJumpCooldown();
                state.setAutoSwing(false);
                state.setLastSwingTimeMillis(0L);
                state.setAutoUse(false);
                state.setAutoSneak(false);
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
            } else if (request.actionType() == ActionRequest.ActionType.GOTO_COORDINATES) {
                startGoto(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.INTERACT_TARGETED_BLOCK) {
                interactTargetedBlock(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.CLOSE_CURRENT_SCREEN) {
                closeCurrentScreen(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.CAPTURE_CONTAINER_SNAPSHOT) {
                captureContainerSnapshot(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.DEPOSIT_ITEM) {
                inventoryActionExecutor.transferItems(client, request, "deposit", true);
            } else if (request.actionType() == ActionRequest.ActionType.WITHDRAW_ITEM) {
                inventoryActionExecutor.transferItems(client, request, "withdraw", false);
            } else if (request.actionType() == ActionRequest.ActionType.CAPTURE_INVENTORY_SNAPSHOT) {
                inventoryActionExecutor.captureInventorySnapshot(client, request, false);
            } else if (request.actionType() == ActionRequest.ActionType.CAPTURE_INVENTORY_DETAILS) {
                inventoryActionExecutor.captureInventorySnapshot(client, request, true);
            } else if (request.actionType() == ActionRequest.ActionType.COUNT_ITEM) {
                inventoryActionExecutor.countItem(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.HAVE_ITEM) {
                inventoryActionExecutor.haveItem(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.DROP_ITEM) {
                inventoryActionExecutor.dropItem(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.SET_AUTOSWING) {
                setAutoSwing(request);
            } else if (request.actionType() == ActionRequest.ActionType.SET_AUTOUSE) {
                setAutoUse(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.SET_AUTOSNEAK) {
                setAutoSneak(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.DISABLE_FARM_ACTIONS) {
                disableFarmActions(client, request);
            }
        } catch (Exception exception) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "exception");
            state.queueChatMessage("action failed");
        } finally {
            state.clearActiveAction();
        }
    }

    private void captureContainerSnapshot(MinecraftClient client, ActionRequest request) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "container_not_open");
            state.queueChatMessage("container not open");
            return;
        }

        List<ContainerSnapshot.Entry> entries = new ArrayList<>();
        List<Slot> slots = inventoryActionExecutor.containerSlots(handledScreen.getScreenHandler().slots);

        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            Slot slot = slots.get(slotIndex);
            ItemStack stack = slot.getStack();

            if (stack.isEmpty()) {
                continue;
            }

            entries.add(new ContainerSnapshot.Entry(
                    slotIndex,
                    Registries.ITEM.getId(stack.getItem()).toString(),
                    stack.getCount()
            ));
        }

        state.setContainerSnapshot(new ContainerSnapshot(System.currentTimeMillis(), slots.size(), List.copyOf(entries)));
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage("snapshot captured");
    }

    private void setAutoSwing(ActionRequest request) {
        boolean enabled = "on".equals(request.actionData());
        state.setAutoSwing(enabled);
        state.setLastSwingTimeMillis(0L);
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), enabled ? "enabled" : "disabled", "");
        state.queueChatMessage(enabled ? "autoswing enabled" : "autoswing disabled");
    }

    private void setAutoUse(MinecraftClient client, ActionRequest request) {
        boolean enabled = "on".equals(request.actionData());
        state.setAutoUse(enabled);

        if (client != null && client.options != null) {
            client.options.useKey.setPressed(enabled);
        }

        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), enabled ? "enabled" : "disabled", "");
        state.queueChatMessage(enabled ? "autouse enabled" : "autouse disabled");
    }

    private void setAutoSneak(MinecraftClient client, ActionRequest request) {
        boolean enabled = "on".equals(request.actionData());
        state.setAutoSneak(enabled);

        if (client != null && client.options != null) {
            client.options.sneakKey.setPressed(enabled);
        }

        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), enabled ? "enabled" : "disabled", "");
        state.queueChatMessage(enabled ? "autosneak enabled" : "autosneak disabled");
    }

    private void disableFarmActions(MinecraftClient client, ActionRequest request) {
        state.setAutoSwing(false);
        state.setLastSwingTimeMillis(0L);
        state.setAutoUse(false);
        state.setAutoSneak(false);

        if (client != null && client.options != null) {
            client.options.useKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
        }

        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage("farm actions disabled");
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

    private void closeCurrentScreen(MinecraftClient client, ActionRequest request) {
        if (client == null || client.player == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "client_unavailable");
            state.queueChatMessage("action failed");
            return;
        }

        if (client.currentScreen == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "no_screen_open");
            state.queueChatMessage("nothing_to_close");
            return;
        }

        client.player.closeScreen();
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage("closed");
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

        lookController.rotateToward(player, target.getEyePos());
        client.options.forwardKey.setPressed(true);
    }

    private void tickFollow(MinecraftClient client) {
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
            stop(client);
            state.setFollowJump(false);
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, activeFollow.actionData());

        if (target == null) {
            String targetName = activeFollow.actionData();
            int replans = followStuckReplans;
            int stuckTicks = movementRecovery.followStuckTicks();
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            clearActiveFollowRouteLocal();
            state.setActiveFollowRoute(targetName, 0, 0, "target_player_not_found", replans, stuckTicks);
            stop(client);
            state.setFollowJump(false);
            return;
        }

        double followDistanceToTarget = player.distanceTo(target);
        if (followDistanceToTarget <= FOLLOW_DISTANCE) {
            logFollowDiagnostics(player, target, true);
            clearDirectionalKeys(client.options);
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

        followController.tickReplan(client, player, target, state, activeFollow.actionData(), followStuckReplans,
                movementRecovery.followStuckTicks(), movementDiagnostics);
        if (followController.consumePlannedSuccessfully()) {
            movementRecovery.resetFollowRecovery();
            movementDiagnostics.setFollowRecovery("none");
            resetFollowStuckTracking();
        }

        if (followController.isRouteEmpty()) {
            if (movementRecovery.tickFollowEmptyRouteRecovery(
                    client,
                    player,
                    target,
                    movementDiagnostics,
                    () -> followController.planRoute(client, player, target, state, activeFollow.actionData(),
                            followStuckReplans, movementRecovery.followStuckTicks(), movementDiagnostics),
                    followController.goalLabel(),
                    followController.routeIndex(),
                    followController.routeLength(),
                    FOLLOW_DISTANCE
            )) {
                return;
            }

            logFollowDiagnostics(player, target, false);
            clearDirectionalKeys(client.options);
            client.options.jumpKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
            state.setFollowJump(false);
            return;
        }

        if (followController.advanceRouteIndex(player)) {
            resetFollowStuckTracking();
        }

        BlockPos waypoint = followController.waypoint();
        double waypointDistance = horizontalDistance(player, waypoint);
        if (isFollowRouteStuck(client, player, target, waypointDistance)) {
            return;
        }

        lookController.rotateToward(player, lookController.followRouteLookTarget(followController.route(),
                followController.routeIndex()));
        boolean jumpWanted = routeExecutor.executeFollowRoute(client.options, player, waypoint, player.distanceTo(target));
        movementDiagnostics.logMovement("follow_route", client.options, player, waypoint, waypoint.getY() - player.getY(),
                jumpWanted,
                followController.routeIndex(),
                followController.routeLength());
        logFollowDiagnostics(player, target, false);
        state.setActiveFollowRoute(
                activeFollow.actionData(),
                followController.routeIndex(),
                followController.routeLength(),
                "success",
                followStuckReplans,
                movementRecovery.followStuckTicks()
        );
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
        clearActiveFollowRoute();
        gotoController.cancel("goto_cancelled");
        routeExecutor.resetJumpCooldown();
        GameOptions options = client.options;
        clearDirectionalKeys(options);
        options.jumpKey.setPressed(false);
        state.setFollowJump(false);
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
        clearActiveFollowRoute();
        gotoController.cancel("goto_cancelled");
        routeExecutor.resetJumpCooldown();
        clearDirectionalKeys(client.options);
        client.options.jumpKey.setPressed(false);
        state.setFollowJump(false);
        client.options.sprintKey.setPressed(false);
        lookController.rotateToward(player, target.getEyePos());
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
        gotoController.cancel("goto_cancelled");
        clearActiveFollowRoute();
        routeExecutor.resetJumpCooldown();
        clearDirectionalKeys(client.options);
        client.options.sprintKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        state.setFollowJump(false);
        activeFollow = request;
        followController.start(client, player, target, state, activeFollow.actionData(), followStuckReplans,
                movementRecovery.followStuckTicks(), movementDiagnostics);
        if (followController.consumePlannedSuccessfully()) {
            movementRecovery.resetFollowRecovery();
            movementDiagnostics.setFollowRecovery("none");
            resetFollowStuckTracking();
        }
        state.queueChatMessage("following " + request.actionData());
    }

    private void clearActiveFollowRoute() {
        followController.clear(state);
        resetFollowState();
    }

    private void clearActiveFollowRouteLocal() {
        followController.clearLocal();
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
        clearDirectionalKeys(options);
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
                                       double waypointDistance) {
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
            stop(client);
            return true;
        }

        resetFollowStuckTracking();
        followController.planRoute(client, player, target, state, activeFollow.actionData(), followStuckReplans,
                movementRecovery.followStuckTicks(), movementDiagnostics);
        if (followController.consumePlannedSuccessfully()) {
            movementRecovery.resetFollowRecovery();
            movementDiagnostics.setFollowRecovery("none");
            resetFollowStuckTracking();
        }
        return true;
    }

    private void startGoto(MinecraftClient client, ActionRequest request) {
        gotoController.start(client, request, () -> {
            timedMovement = null;
            timedMovementTicksRemaining = 0;
            activeApproach = null;
            activeFollow = null;
            clearActiveFollowRoute();
        }, () -> stop(client));
    }

    private void stop(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        GameOptions options = client.options;
        clearDirectionalKeys(options);
        options.jumpKey.setPressed(false);
        routeExecutor.resetJumpCooldown();
        state.setFollowJump(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }

    private void tickAutoUse(MinecraftClient client) {
        if (!state.autoUse()) {
            return;
        }

        if (client == null || client.options == null) {
            state.setAutoUse(false);
            return;
        }

        client.options.useKey.setPressed(true);
    }

    private void tickAutoSneak(MinecraftClient client) {
        if (!state.autoSneak()) {
            return;
        }

        if (client == null || client.options == null) {
            state.setAutoSneak(false);
            return;
        }

        client.options.sneakKey.setPressed(true);
    }

    private void tickAutoSwing(MinecraftClient client) {
        if (!state.autoSwing()) {
            return;
        }

        if (client == null || client.player == null) {
            state.setAutoSwing(false);
            state.setLastSwingTimeMillis(0L);
            return;
        }

        long now = System.currentTimeMillis();

        if (now - state.lastSwingTimeMillis() < AUTOSWING_INTERVAL_MILLIS) {
            return;
        }

        client.player.swingHand(Hand.MAIN_HAND);
        state.setLastSwingTimeMillis(now);
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
                followController.goalLabel(),
                followController.routeIndex(),
                followController.routeLength(),
                FOLLOW_DISTANCE,
                movementRecovery.followRecoveryActive()
        );
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

        lookController.rotateToward(player, target.getEyePos());
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

}
