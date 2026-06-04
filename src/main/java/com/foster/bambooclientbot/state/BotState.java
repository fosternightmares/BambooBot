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
}
