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
    private int activeGotoReplans;
    private int activeGotoStuckTicks;
    private GotoResult lastGotoResult;
    private String activeFollowTarget = "";
    private int activeFollowRouteIndex;
    private int activeFollowRouteLength;
    private String activeFollowRouteResult = "none";
    private int activeFollowRouteReplans;
    private int activeFollowRouteStuckTicks;
    private String movementMode = "none";
    private String movementWaypoint = "none";
    private double movementWaypointDeltaY;
    private boolean movementHorizontalCollision;
    private boolean movementOnGround;
    private boolean movementJumpPressed;
    private boolean movementSprintPressed;
    private int movementRouteIndex;
    private int movementRouteLength;
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

    public void setActiveGoto(GotoTarget target, double distance, int routeIndex, int routeLength,
                              int replans, int stuckTicks) {
        this.activeGotoTarget = target;
        this.activeGotoDistance = distance;
        this.activeGotoRouteIndex = routeIndex;
        this.activeGotoRouteLength = routeLength;
        this.activeGotoReplans = replans;
        this.activeGotoStuckTicks = stuckTicks;
    }

    public void clearActiveGoto() {
        activeGotoTarget = null;
        activeGotoDistance = 0.0;
        activeGotoRouteIndex = 0;
        activeGotoRouteLength = 0;
        activeGotoReplans = 0;
        activeGotoStuckTicks = 0;
        clearMovementDiagnostics();
    }

    public void setLastGotoResult(GotoResult lastGotoResult) {
        this.lastGotoResult = lastGotoResult;
    }

    public String gotoStatus() {
        if (activeGotoTarget == null) {
            return "gotoTarget=none";
        }

        return "gotoTarget=" + activeGotoTarget.format()
                + " gotoDistance=" + String.format(java.util.Locale.ROOT, "%.1f", activeGotoDistance);
    }

    public void setActiveFollowRoute(String targetPlayer, int routeIndex, int routeLength, String result,
                                     int replans, int stuckTicks) {
        activeFollowTarget = targetPlayer;
        activeFollowRouteIndex = routeIndex;
        activeFollowRouteLength = routeLength;
        activeFollowRouteResult = result;
        activeFollowRouteReplans = replans;
        activeFollowRouteStuckTicks = stuckTicks;
    }

    public void clearActiveFollowRoute() {
        activeFollowTarget = "";
        activeFollowRouteIndex = 0;
        activeFollowRouteLength = 0;
        activeFollowRouteResult = "none";
        activeFollowRouteReplans = 0;
        activeFollowRouteStuckTicks = 0;
        clearMovementDiagnostics();
    }

    public String followRouteStatus() {
        if (activeFollowTarget == null || activeFollowTarget.isBlank()) {
            return "followTarget=none";
        }

        return "followTarget=" + activeFollowTarget;
    }

    public String routeStatus() {
        if (activeGotoTarget != null) {
            return "routeActive=true"
                    + " routeIndex=" + activeGotoRouteIndex
                    + " routeLength=" + activeGotoRouteLength
                    + " routeResult=success"
                    + " stuckTicks=" + activeGotoStuckTicks
                    + " replans=" + activeGotoReplans
                    + " " + movementDiagnosticsStatus();
        }

        if (activeFollowTarget != null && !activeFollowTarget.isBlank()) {
            return "routeActive=true"
                    + " routeIndex=" + activeFollowRouteIndex
                    + " routeLength=" + activeFollowRouteLength
                    + " routeResult=" + normalizeRouteResult(activeFollowRouteResult)
                    + " stuckTicks=" + activeFollowRouteStuckTicks
                    + " replans=" + activeFollowRouteReplans
                    + " " + movementDiagnosticsStatus();
        }

        return "routeActive=false routeIndex=0 routeLength=0 routeResult=none stuckTicks=0 replans=0 "
                + movementDiagnosticsStatus();
    }

    public void setMovementDiagnostics(String mode, String waypoint, double waypointDeltaY,
                                       boolean horizontalCollision, boolean onGround,
                                       boolean jumpPressed, boolean sprintPressed,
                                       int routeIndex, int routeLength) {
        movementMode = mode == null || mode.isBlank() ? "none" : mode;
        movementWaypoint = waypoint == null || waypoint.isBlank() ? "none" : waypoint;
        movementWaypointDeltaY = waypointDeltaY;
        movementHorizontalCollision = horizontalCollision;
        movementOnGround = onGround;
        movementJumpPressed = jumpPressed;
        movementSprintPressed = sprintPressed;
        movementRouteIndex = routeIndex;
        movementRouteLength = routeLength;
    }

    public void clearMovementDiagnostics() {
        movementMode = "none";
        movementWaypoint = "none";
        movementWaypointDeltaY = 0.0;
        movementHorizontalCollision = false;
        movementOnGround = false;
        movementJumpPressed = false;
        movementSprintPressed = false;
        movementRouteIndex = 0;
        movementRouteLength = 0;
    }

    private String movementDiagnosticsStatus() {
        return "movementMode=" + movementMode
                + " waypoint=" + movementWaypoint
                + " waypointDeltaY=" + String.format(java.util.Locale.ROOT, "%.2f", movementWaypointDeltaY)
                + " horizontalCollision=" + movementHorizontalCollision
                + " onGround=" + movementOnGround
                + " jumpPressed=" + movementJumpPressed
                + " sprintPressed=" + movementSprintPressed
                + " routeIndex=" + movementRouteIndex
                + " routeLength=" + movementRouteLength;
    }

    public String recentRouteStatus() {
        if (lastGotoResult == null) {
            return "lastGoto=none lastGotoResult=none";
        }

        return "lastGoto=" + lastGotoResult.targetLabel()
                + " lastGotoResult=" + normalizeRouteResult(lastGotoResult.result());
    }

    private String normalizeRouteResult(String result) {
        if (result == null || result.isBlank()) {
            return "none";
        }

        return switch (result) {
            case "success", "stuck", "timeout", "none" -> result;
            default -> "failed";
        };
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
