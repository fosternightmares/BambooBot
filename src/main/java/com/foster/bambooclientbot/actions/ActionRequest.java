package com.foster.bambooclientbot.actions;

public class ActionRequest {
    public enum ActionType {
        STOP,
        FORWARD,
        BACK,
        LEFT,
        RIGHT,
        LOOK_AT_PLAYER
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

    public ActionRequest(ActionType actionType, String actionData) {
        this(actionType, actionData, 0, 0);
    }

    public ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds) {
        this.actionType = actionType;
        this.actionStatus = ActionStatus.IDLE;
        this.actionData = actionData;
        this.durationTicks = durationTicks;
        this.durationSeconds = durationSeconds;
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
}
