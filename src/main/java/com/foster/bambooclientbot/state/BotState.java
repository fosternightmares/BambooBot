package com.foster.bambooclientbot.state;

import com.foster.bambooclientbot.actions.ActionRequest;
import com.foster.bambooclientbot.commands.CommandRequest;
import java.util.ArrayDeque;
import java.util.Queue;

public class BotState {
    private final Queue<CommandRequest> pendingCommands = new ArrayDeque<>();
    private final Queue<String> pendingChatMessages = new ArrayDeque<>();
    private final Queue<ActionRequest> pendingActions = new ArrayDeque<>();
    private LookedAtBlock lookedAtBlock;
    private ContainerState containerState = ContainerState.closed();
    private ContainerSnapshot containerSnapshot;
    private InventorySnapshot inventorySnapshot;
    private String activeAction = "none";
    private String lastAction = "none";
    private String lastActionResult = "none";
    private String lastActionReason = "";
    private TransferResult lastTransferResult;
    private DropResult lastDropResult;
    private GotoTarget activeGotoTarget;
    private double activeGotoDistance;
    private int activeGotoRouteIndex;
    private int activeGotoRouteLength;
    private GotoResult lastGotoResult;
    private boolean followJump;
    private boolean autoSwing;
    private long lastSwingTimeMillis;
    private boolean autoUse;
    private boolean autoSneak;

    public void queueCommand(CommandRequest request) {
        pendingCommands.add(request);
    }

    public CommandRequest pollCommand() {
        return pendingCommands.poll();
    }

    public void queueChatMessage(String message) {
        pendingChatMessages.add(message);
    }

    public String pollChatMessage() {
        return pendingChatMessages.poll();
    }

    public void queueAction(ActionRequest request) {
        request.setStatus(ActionRequest.ActionStatus.QUEUED);
        pendingActions.add(request);
    }

    public ActionRequest pollAction() {
        return pendingActions.poll();
    }

    public void setLookedAtBlock(LookedAtBlock lookedAtBlock) {
        this.lookedAtBlock = lookedAtBlock;
    }

    public LookedAtBlock lookedAtBlock() {
        return lookedAtBlock;
    }

    public void setContainerState(ContainerState containerState) {
        this.containerState = containerState;
    }

    public ContainerState containerState() {
        return containerState;
    }

    public void setContainerSnapshot(ContainerSnapshot containerSnapshot) {
        this.containerSnapshot = containerSnapshot;
    }

    public ContainerSnapshot containerSnapshot() {
        return containerSnapshot;
    }

    public void setInventorySnapshot(InventorySnapshot inventorySnapshot) {
        this.inventorySnapshot = inventorySnapshot;
    }

    public InventorySnapshot inventorySnapshot() {
        return inventorySnapshot;
    }

    public void setActiveAction(String activeAction) {
        this.activeAction = activeAction;
    }

    public void clearActiveAction() {
        activeAction = "none";
    }

    public String activeAction() {
        return activeAction;
    }

    public void setLastActionResult(String lastAction, String result, String reason) {
        this.lastAction = lastAction;
        this.lastActionResult = result;
        this.lastActionReason = reason;
    }

    public String lastActionStatus() {
        if ("none".equals(lastAction)) {
            return "lastAction=none";
        }

        if (lastActionReason == null || lastActionReason.isBlank()) {
            return "lastAction=" + lastAction + " result=" + lastActionResult;
        }

        return "lastAction=" + lastAction + " result=" + lastActionResult + " reason=" + lastActionReason;
    }

    public void setLastTransferResult(TransferResult lastTransferResult) {
        this.lastTransferResult = lastTransferResult;
    }

    public String lastTransferStatus() {
        if (lastTransferResult == null) {
            return "lastTransfer=none";
        }

        return lastTransferResult.format();
    }

    public void setLastDropResult(DropResult lastDropResult) {
        this.lastDropResult = lastDropResult;
    }

    public String lastDropStatus() {
        if (lastDropResult == null) {
            return "lastDrop=none";
        }

        return lastDropResult.format();
    }

    public void setActiveGoto(GotoTarget target, double distance, int routeIndex, int routeLength) {
        this.activeGotoTarget = target;
        this.activeGotoDistance = distance;
        this.activeGotoRouteIndex = routeIndex;
        this.activeGotoRouteLength = routeLength;
    }

    public void clearActiveGoto() {
        activeGotoTarget = null;
        activeGotoDistance = 0.0;
        activeGotoRouteIndex = 0;
        activeGotoRouteLength = 0;
    }

    public void setLastGotoResult(GotoResult lastGotoResult) {
        this.lastGotoResult = lastGotoResult;
    }

    public String gotoStatus() {
        String lastGoto = lastGotoResult == null ? "lastGoto=none" : lastGotoResult.format();

        if (activeGotoTarget == null) {
            return "activeGoto=none " + lastGoto;
        }

        return "activeGoto=true target=" + activeGotoTarget.format()
                + " routeIndex=" + activeGotoRouteIndex
                + " routeLength=" + activeGotoRouteLength
                + " distance=" + String.format(java.util.Locale.ROOT, "%.1f", activeGotoDistance)
                + " " + lastGoto;
    }

    public void setFollowJump(boolean followJump) {
        this.followJump = followJump;
    }

    public boolean followJump() {
        return followJump;
    }

    public void setAutoSwing(boolean autoSwing) {
        this.autoSwing = autoSwing;
    }

    public boolean autoSwing() {
        return autoSwing;
    }

    public void setLastSwingTimeMillis(long lastSwingTimeMillis) {
        this.lastSwingTimeMillis = lastSwingTimeMillis;
    }

    public long lastSwingTimeMillis() {
        return lastSwingTimeMillis;
    }

    public void setAutoUse(boolean autoUse) {
        this.autoUse = autoUse;
    }

    public boolean autoUse() {
        return autoUse;
    }

    public void setAutoSneak(boolean autoSneak) {
        this.autoSneak = autoSneak;
    }

    public boolean autoSneak() {
        return autoSneak;
    }
}
