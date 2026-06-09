package com.foster.bambooclientbot.state;

import com.foster.bambooclientbot.actions.ActionRequest;
import com.foster.bambooclientbot.commands.CommandRequest;
import com.foster.bambooclientbot.logging.BambooBotLog;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

/*
 * Source-of-truth rule: when Minecraft already owns live client state, read it
 * from Minecraft at the decision/use site. BotState may queue requests, retain
 * command results, and keep diagnostics or explicit snapshots, but those cached
 * values must not become authoritative copies of live client state.
 */
public class BotState {
    private final Queue<CommandRequest> pendingCommands = new ArrayDeque<>();
    private final Queue<String> pendingChatMessages = new ArrayDeque<>();
    private final Queue<ActionRequest> pendingActions = new ArrayDeque<>();
    // Sensor cache refreshed from client.crosshairTarget; consumers must treat it as a snapshot.
    private LookedAtBlock lookedAtBlock;
    // Sensor cache refreshed from client.currentScreen; client.currentScreen remains authoritative.
    private ContainerState containerState = ContainerState.closed();
    // Explicit command snapshots; live container/inventory contents remain Minecraft-owned.
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
    private boolean followJump;
    private boolean autoSwing;
    private long lastSwingTimeMillis;
    private boolean autoUse;
    private boolean autoSneak;
    private boolean autoEatActive;
    private long lastAutoEatTimeMillis;
    private String lastAutoEatResult = "hunger_ok";
    private float currentHealth = -1.0f;
    private float previousHealth = -1.0f;
    private float damageAmount;
    private String damageType = "none";
    private String sourceEntity = "none";
    private String attackerEntity = "none";
    private UUID attackerUuid;
    private List<ThreatInfo> threats = List.of();

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

    public ActionRequest peekAction() {
        return pendingActions.peek();
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
                    + " replans=" + activeGotoReplans;
        }

        if (activeFollowTarget != null && !activeFollowTarget.isBlank()) {
            return "routeActive=true"
                    + " routeIndex=" + activeFollowRouteIndex
                    + " routeLength=" + activeFollowRouteLength
                    + " routeResult=" + normalizeRouteResult(activeFollowRouteResult)
                    + " stuckTicks=" + activeFollowRouteStuckTicks
                    + " replans=" + activeFollowRouteReplans;
        }

        return "routeActive=false routeIndex=0 routeLength=0 routeResult=none stuckTicks=0 replans=0";
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

    public void setAutoEatActive(boolean autoEatActive) {
        this.autoEatActive = autoEatActive;
    }

    public boolean autoEatActive() {
        return autoEatActive;
    }

    public void setLastAutoEatTimeMillis(long lastAutoEatTimeMillis) {
        this.lastAutoEatTimeMillis = lastAutoEatTimeMillis;
    }

    public long lastAutoEatTimeMillis() {
        return lastAutoEatTimeMillis;
    }

    public void setLastAutoEatResult(String lastAutoEatResult) {
        this.lastAutoEatResult = lastAutoEatResult;
    }

    public String autoEatStatus() {
        long ageMillis = lastAutoEatTimeMillis <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - lastAutoEatTimeMillis);
        return "autoEatActive=" + autoEatActive
                + " lastAutoEatAgeMs=" + ageMillis
                + " lastAutoEatResult=" + lastAutoEatResult;
    }

    public void observeDamage(float observedHealth, String observedDamageType, String observedSourceEntity,
                              String observedAttackerEntity, UUID observedAttackerUuid) {
        float lastObservedHealth = currentHealth;

        previousHealth = lastObservedHealth < 0.0f ? observedHealth : lastObservedHealth;
        currentHealth = observedHealth;

        if (lastObservedHealth < 0.0f || observedHealth >= lastObservedHealth) {
            return;
        }

        damageAmount = lastObservedHealth - observedHealth;
        damageType = safeValue(observedDamageType);
        sourceEntity = safeValue(observedSourceEntity);
        attackerEntity = safeValue(observedAttackerEntity);
        attackerUuid = observedAttackerUuid;
        BambooBotLog.info("DAMAGE amount=" + formatHealth(damageAmount)
                + " type=" + damageType
                + " attacker=" + attackerEntity
                + " uuid=" + (attackerUuid == null ? "none" : attackerUuid)
                + " health=" + formatHealth(currentHealth));
    }

    public String damageStatus() {
        return "currentHealth=" + formatHealth(currentHealth)
                + " previousHealth=" + formatHealth(previousHealth)
                + " damageAmount=" + formatHealth(damageAmount)
                + " damageType=" + damageType
                + " sourceEntity=" + sourceEntity
                + " attackerEntity=" + attackerEntity
                + " attackerUuid=" + (attackerUuid == null ? "none" : attackerUuid);
    }

    public void setThreats(List<ThreatInfo> threats) {
        this.threats = threats == null ? List.of() : List.copyOf(threats);
    }

    public List<ThreatInfo> threats() {
        return threats;
    }

    public int threatCount() {
        return threats.size();
    }

    public boolean underThreat() {
        for (ThreatInfo threat : threats) {
            if (threat.targetingBot()) {
                return true;
            }
        }

        return false;
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String formatHealth(float health) {
        if (health < 0.0f) {
            return "unknown";
        }

        return String.format(Locale.ROOT, "%.1f", health);
    }
}
