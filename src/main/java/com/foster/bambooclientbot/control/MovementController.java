package com.foster.bambooclientbot.control;

import com.foster.bambooclientbot.actions.ActionRequest;
import com.foster.bambooclientbot.actions.RouteExecutor;
import com.foster.bambooclientbot.logging.BambooBotLog;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

public class MovementController {
    private int requestCount;
    private boolean conflictLogged;

    public void beginTick() {
        requestCount = 0;
        conflictLogged = false;
    }

    public void applyRouteMovement(GameOptions options, RouteExecutor.RouteMovement movement, String source) {
        recordRequest(source);
        clearDirectionalKeysRaw(options);
        options.forwardKey.setPressed(true);
        options.jumpKey.setPressed(movement.jumpPressed());
        options.sprintKey.setPressed(movement.sprintPressed());
    }

    public void applyFollowDirect(GameOptions options, boolean sprintPressed, boolean jumpPressed) {
        recordRequest("follow_direct");
        clearDirectionalKeysRaw(options);
        options.forwardKey.setPressed(true);
        options.sprintKey.setPressed(sprintPressed);
        options.jumpKey.setPressed(jumpPressed);
    }

    public void applyForwardNudge(GameOptions options) {
        recordRequest("forward_nudge");
        clearDirectionalKeysRaw(options);
        options.forwardKey.setPressed(true);
        options.jumpKey.setPressed(false);
        options.sprintKey.setPressed(false);
    }

    public void pressForward(GameOptions options, String source) {
        recordRequest(source);
        options.forwardKey.setPressed(true);
    }

    public void applyManualMovement(GameOptions options, ActionRequest.ActionType actionType) {
        recordRequest("manual_" + actionType.name().toLowerCase());
        clearDirectionalKeysRaw(options);
        options.jumpKey.setPressed(false);
        options.sprintKey.setPressed(false);
        movementKey(options, actionType).setPressed(true);
    }

    public void clearRouteMovement(GameOptions options, String source) {
        recordRequest(source);
        clearDirectionalKeysRaw(options);
        options.jumpKey.setPressed(false);
        options.sprintKey.setPressed(false);
    }

    public void stopAll(GameOptions options) {
        recordRequest("stop_all");
        clearDirectionalKeysRaw(options);
        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }

    public void setUse(GameOptions options, boolean pressed, String source) {
        recordRequest(source);
        options.useKey.setPressed(pressed);
    }

    public void setSneak(GameOptions options, boolean pressed, String source) {
        recordRequest(source);
        options.sneakKey.setPressed(pressed);
    }

    private void clearDirectionalKeysRaw(GameOptions options) {
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

    private void recordRequest(String source) {
        requestCount++;

        if (requestCount > 1 && !conflictLogged) {
            conflictLogged = true;
            BambooBotLog.warn("MOVE_CONFLICT count=" + requestCount + " source=" + source);
        }
    }
}
