package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.navigation.NavigationGrid;
import com.foster.bambooclientbot.navigation.PathPlanner;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.Hand;

public class ActionExecutor {
    private static final double LOOK_AT_RANGE = 32.0;
    private static final long AUTOSWING_INTERVAL_MILLIS = 1_000L;

    private final BotState state;
    private final PathPlanner pathPlanner = new PathPlanner(new NavigationGrid());
    private final LookController lookController = new LookController();
    private final MovementController movementController = new MovementController();
    private final MovementDiagnostics movementDiagnostics = new MovementDiagnostics();
    private final FollowController followController;
    private final RouteExecutor routeExecutor;
    private final MovementRecovery movementRecovery;
    private final SimpleMovementController simpleMovementController;
    private final InventoryActionExecutor inventoryActionExecutor;
    private final ContainerActionExecutor containerActionExecutor;
    private final GotoController gotoController;

    public ActionExecutor(BotState state) {
        this.state = state;
        routeExecutor = new RouteExecutor(state);
        movementRecovery = new MovementRecovery(state, lookController, movementController);
        simpleMovementController = new SimpleMovementController(state, lookController, routeExecutor, movementController);
        inventoryActionExecutor = new InventoryActionExecutor(state, lookController);
        containerActionExecutor = new ContainerActionExecutor(state, inventoryActionExecutor);
        followController = new FollowController(state, pathPlanner, lookController, routeExecutor, movementRecovery,
                movementController, movementDiagnostics);
        gotoController = new GotoController(state, pathPlanner, lookController, routeExecutor, movementRecovery,
                movementDiagnostics, movementController);
    }

    public void tick(MinecraftClient client) {
        lookController.beginTick();
        movementController.beginTick();
        ActionRequest request = state.pollAction();

        if (request != null) {
            executeAction(client, request);
            tickAutoSneak(client);
            tickAutoUse(client);
            tickAutoSwing(client);
            return;
        }

        simpleMovementController.tickApproach(client, this::stop, (player, targetName) -> findTargetPlayer(client, player, targetName));
        followController.tick(client, this::stop, (player, targetName) -> findTargetPlayer(client, player, targetName));
        gotoController.tick(client, () -> stop(client));
        simpleMovementController.tickTimedMovement(client, this::stop);
        tickAutoSneak(client);
        tickAutoUse(client);
        tickAutoSwing(client);
    }

    private void executeAction(MinecraftClient client, ActionRequest request) {
        request.setStatus(ActionRequest.ActionStatus.RUNNING);
        state.setActiveAction(request.actionType().name());

        try {
            if (request.actionType() == ActionRequest.ActionType.STOP) {
                simpleMovementController.cancelActiveMovement();
                followController.cancelActiveFollow();
                gotoController.cancel("goto_cancelled");
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
                containerActionExecutor.interactTargetedBlock(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.CLOSE_CURRENT_SCREEN) {
                containerActionExecutor.closeCurrentScreen(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.CAPTURE_CONTAINER_SNAPSHOT) {
                containerActionExecutor.captureContainerSnapshot(client, request);
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
            movementController.setUse(client.options, enabled, "autouse_set");
        }

        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), enabled ? "enabled" : "disabled", "");
        state.queueChatMessage(enabled ? "autouse enabled" : "autouse disabled");
    }

    private void setAutoSneak(MinecraftClient client, ActionRequest request) {
        boolean enabled = "on".equals(request.actionData());
        state.setAutoSneak(enabled);

        if (client != null && client.options != null) {
            movementController.setSneak(client.options, enabled, "autosneak_set");
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
            movementController.setUse(client.options, false, "farm_actions_off");
            movementController.setSneak(client.options, false, "farm_actions_off");
        }

        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage("farm actions disabled");
    }

    private void move(MinecraftClient client, ActionRequest request, String direction) {
        simpleMovementController.move(client, request, direction, () -> {
            followController.cancelActiveFollow();
            gotoController.cancel("goto_cancelled");
        });
    }

    private void startApproach(MinecraftClient client, ActionRequest request) {
        simpleMovementController.startApproach(client, request, this::stop,
                (player, targetName) -> findTargetPlayer(client, player, targetName),
                () -> {
                    followController.cancelActiveFollow();
                    gotoController.cancel("goto_cancelled");
                });
    }

    private void startFollow(MinecraftClient client, ActionRequest request) {
        followController.startFollow(client, request, () -> {
            simpleMovementController.cancelActiveMovement();
            gotoController.cancel("goto_cancelled");
        }, (player, targetName) -> findTargetPlayer(client, player, targetName));
    }

    private void startGoto(MinecraftClient client, ActionRequest request) {
        gotoController.start(client, request, () -> {
            simpleMovementController.cancelActiveMovement();
            followController.cancelActiveFollow();
        }, () -> stop(client));
    }

    private void stop(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        GameOptions options = client.options;
        movementController.stopAll(options);
        routeExecutor.resetJumpCooldown();
        state.setFollowJump(false);
    }

    private void tickAutoUse(MinecraftClient client) {
        if (!state.autoUse()) {
            return;
        }

        if (client == null || client.options == null) {
            state.setAutoUse(false);
            return;
        }

        movementController.setUse(client.options, true, "autouse_tick");
    }

    private void tickAutoSneak(MinecraftClient client) {
        if (!state.autoSneak()) {
            return;
        }

        if (client == null || client.options == null) {
            state.setAutoSneak(false);
            return;
        }

        movementController.setSneak(client.options, true, "autosneak_tick");
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
