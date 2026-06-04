package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.LookedAtBlock;
import com.foster.bambooclientbot.state.GotoTarget;

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
        WITHDRAW_ITEM,
        CAPTURE_INVENTORY_SNAPSHOT,
        CAPTURE_INVENTORY_DETAILS,
        COUNT_ITEM,
        HAVE_ITEM,
        DROP_ITEM,
        SET_AUTOSWING,
        SET_AUTOUSE,
        SET_AUTOSNEAK,
        DISABLE_FARM_ACTIONS,
        GOTO_COORDINATES
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
    private final String itemLabel;
    private final int requestedCount;
    private final GotoTarget gotoTarget;

    public ActionRequest(ActionType actionType, String actionData) {
        this(actionType, actionData, 0, 0, null, "", "", 0, null);
    }

    public ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds) {
        this(actionType, actionData, durationTicks, durationSeconds, null, "", "", 0, null);
    }

    public ActionRequest(ActionType actionType, LookedAtBlock targetedBlock) {
        this(actionType, "", 0, 0, targetedBlock, "", "", 0, null);
    }

    public ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds, LookedAtBlock targetedBlock) {
        this(actionType, actionData, durationTicks, durationSeconds, targetedBlock, "", "", 0, null);
    }

    private ActionRequest(ActionType actionType, String actionData, int durationTicks, int durationSeconds,
                          LookedAtBlock targetedBlock, String itemId, String itemLabel, int requestedCount,
                          GotoTarget gotoTarget) {
        this.actionType = actionType;
        this.actionStatus = ActionStatus.IDLE;
        this.actionData = actionData;
        this.durationTicks = durationTicks;
        this.durationSeconds = durationSeconds;
        this.targetedBlock = targetedBlock;
        this.itemId = itemId;
        this.itemLabel = itemLabel;
        this.requestedCount = requestedCount;
        this.gotoTarget = gotoTarget;
    }

    public static ActionRequest itemTransfer(ActionType actionType, String itemId, int requestedCount) {
        return new ActionRequest(actionType, "", 0, 0, null, itemId, "", requestedCount, null);
    }

    public static ActionRequest itemQuery(ActionType actionType, String itemId, String itemLabel) {
        return new ActionRequest(actionType, "", 0, 0, null, itemId, itemLabel, 0, null);
    }

    public static ActionRequest itemQuery(ActionType actionType, String itemId, String itemLabel, int requestedCount) {
        return new ActionRequest(actionType, "", 0, 0, null, itemId, itemLabel, requestedCount, null);
    }

    public static ActionRequest targetedItem(ActionType actionType, String targetPlayer, String itemId, String itemLabel, int requestedCount) {
        return new ActionRequest(actionType, targetPlayer, 0, 0, null, itemId, itemLabel, requestedCount, null);
    }

    public static ActionRequest gotoCoordinates(GotoTarget target) {
        return new ActionRequest(ActionType.GOTO_COORDINATES, "", 0, 0, null, "", "", 0, target);
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

    public String itemLabel() {
        return itemLabel;
    }

    public int requestedCount() {
        return requestedCount;
    }

    public GotoTarget gotoTarget() {
        return gotoTarget;
    }
}
