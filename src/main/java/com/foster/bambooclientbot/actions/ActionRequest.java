package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.LookedAtBlock;

public class ActionRequest {
    public enum ActionType {
        STOP,
        FORWARD,
        BACK,
        LEFT,
        RIGHT,
        LOOK_AT_PLAYER,
        APPROACH_PLAYER,
        FOLLOW_PLAYER,
        INTERACT_TARGETED_BLOCK
    }

    public enum ActionStatus {
        IDLE,
        QUEUED,
        RUNNING,
        COMPLETE,
        FAILED
    }

    private final ActionType actionType;
    private ActionStatus actionStatus;
    private final String actionData;
    private final int durationTicks;
    private final int durationSeconds;
    private final LookedAtBlock targetedBlock;

    public ActionRequest(ActionType actionType, String actionData) {
        this(actionType, actionData, 0, 0, null);
    }

    public ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds) {
        this(actionType, actionData, durationTicks, durationSeconds, null);
    }

    public ActionRequest(ActionType actionType, LookedAtBlock targetedBlock) {
        this(actionType, "", 0, 0, targetedBlock);
    }

    public ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds, LookedAtBlock targetedBlock) {
        this.actionType = actionType;
        this.actionStatus = ActionStatus.IDLE;
        this.actionData = actionData;
        this.durationTicks = durationTicks;
        this.durationSeconds = durationSeconds;
        this.targetedBlock = targetedBlock;
    }

    public ActionType actionType() {
        return actionType;
    }

    public ActionStatus actionStatus() {
        return actionStatus;
    }

    public void setStatus(ActionStatus actionStatus) {
        this.actionStatus = actionStatus;
    }

    public String actionData() {
        return actionData;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public LookedAtBlock targetedBlock() {
        return targetedBlock;
    }
}
