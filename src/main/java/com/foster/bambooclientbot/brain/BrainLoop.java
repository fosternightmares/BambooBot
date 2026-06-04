package com.foster.bambooclientbot.brain;

import com.foster.bambooclientbot.commands.CommandRequest;
import com.foster.bambooclientbot.state.BotState;

public class BrainLoop {
    private final BotState state;

    public BrainLoop(BotState state) {
        this.state = state;
    }

    public void tick() {
        CommandRequest request;

        while ((request = state.pollCommand()) != null) {
            if ("status".equals(request.command())) {
                state.queueChatMessage("I'm alive.");
            }
        }
    }
}
