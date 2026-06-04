package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

public class ActionExecutor {
    private final BotState state;

    public ActionExecutor(BotState state) {
        this.state = state;
    }

    public void tick(MinecraftClient client) {
        ActionRequest request = state.pollAction();

        if (request == null) {
            return;
        }

        request.setStatus(ActionRequest.ActionStatus.RUNNING);

        try {
            if (request.actionType() == ActionRequest.ActionType.STOP) {
                stop(client);
                request.setStatus(ActionRequest.ActionStatus.COMPLETE);
                state.queueChatMessage("stopped");
            }
        } catch (Exception exception) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
        }
    }

    private void stop(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        GameOptions options = client.options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }
}
