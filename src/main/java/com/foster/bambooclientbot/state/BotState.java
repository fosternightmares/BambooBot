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
    private ContainerSnapshot containerSnapshot = new ContainerSnapshot(0L, 0, java.util.List.of());
    private String activeAction = "none";
    private String lastAction = "none";
    private String lastActionResult = "none";
    private String lastActionReason = "";

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
}
