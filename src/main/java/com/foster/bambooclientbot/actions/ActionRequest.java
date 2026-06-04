package com.foster.bambooclientbot.actions;

public class ActionRequest {
    public enum ActionType {
        STOP
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

    public ActionRequest(ActionType actionType, String actionData) {
        this.actionType = actionType;
        this.actionStatus = ActionStatus.IDLE;
        this.actionData = actionData;
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
}
