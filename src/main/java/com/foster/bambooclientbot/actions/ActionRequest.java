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
        INTERACT_TARGETED_BLOCK,
        CLOSE_CURRENT_SCREEN,
        CAPTURE_CONTAINER_SNAPSHOT,
        DEPOSIT_ITEM,
        WITHDRAW_ITEM
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
    private final String itemId;
    private final int requestedCount;

    public ActionRequest(ActionType actionType, String actionData) {
        this(actionType, actionData, 0, 0, null, "", 0);
    }

    public ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds) {
        this(actionType, actionData, durationTicks, durationSeconds, null, "", 0);
    }

    public ActionRequest(ActionType actionType, LookedAtBlock targetedBlock) {
        this(actionType, "", 0, 0, targetedBlock, "", 0);
    }

    public ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds, LookedAtBlock targetedBlock) {
        this(actionType, actionData, durationTicks, durationSeconds, targetedBlock, "", 0);
    }

    private ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds,
                          LookedAtBlock targetedBlock, String itemId, int requestedCount) {
        this.actionType = actionType;
        this.actionStatus = ActionStatus.IDLE;
        this.actionData = actionData;
        this.durationTicks = durationTicks;
        this.durationSeconds = durationSeconds;
        this.targetedBlock = targetedBlock;
        this.itemId = itemId;
        this.requestedCount = requestedCount;
    }

    public static ActionRequest itemTransfer(ActionType actionType, String itemId, int requestedCount) {
        return new ActionRequest(actionType, "", 0, 0, null, itemId, requestedCount);
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

    public String itemId() {
        return itemId;
    }

    public int requestedCount() {
        return requestedCount;
    }
}
