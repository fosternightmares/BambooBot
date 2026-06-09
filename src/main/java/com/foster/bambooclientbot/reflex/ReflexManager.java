package com.foster.bambooclientbot.reflex;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.logging.BambooBotLog;
import com.foster.bambooclientbot.state.BotState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public class ReflexManager {
    private final List<Reflex> reflexes = new ArrayList<>();

    public void register(Reflex reflex) {
        if (reflex != null) {
            reflexes.add(reflex);
        }
    }

    public void tick(MinecraftClient client, BotState state, ActionExecutor actions) {
        for (Reflex reflex : reflexes) {
            tickReflex(reflex, client, state, actions);
        }
    }

    private void tickReflex(Reflex reflex, MinecraftClient client, BotState state, ActionExecutor actions) {
        try {
            if (reflex.isEnabled(state)) {
                reflex.tick(client, state, actions);
            }
        } catch (Exception exception) {
            BambooBotLog.error("reflex failed name=" + reflexName(reflex)
                    + " error=" + exception.getClass().getSimpleName()
                    + " message=" + exception.getMessage());
        }
    }

    private String reflexName(Reflex reflex) {
        try {
            String name = reflex.name();
            return name == null || name.isBlank() ? "unnamed" : name;
        } catch (Exception ignored) {
            return "unnamed";
        }
    }
}
