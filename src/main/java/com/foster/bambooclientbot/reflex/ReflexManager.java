package com.foster.bambooclientbot.reflex;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.intent.IntentPlan;
import com.foster.bambooclientbot.intent.IntentRequest;
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

    public List<IntentRequest> collectIntents(MinecraftClient client, BotState state) {
        List<IntentRequest> requests = new ArrayList<>();

        for (Reflex reflex : reflexes) {
            collectReflexIntents(reflex, client, state, requests);
        }

        return List.copyOf(requests);
    }

    public void tick(MinecraftClient client, BotState state, ActionExecutor actions, IntentPlan intentPlan) {
        for (Reflex reflex : reflexes) {
            tickReflex(reflex, client, state, actions, intentPlan);
        }
    }

    private void collectReflexIntents(Reflex reflex, MinecraftClient client, BotState state,
                                      List<IntentRequest> requests) {
        try {
            if (reflex.isEnabled(state)) {
                requests.addAll(reflex.collectIntents(client, state));
            }
        } catch (Exception exception) {
            BambooBotLog.error("reflex intent failed name=" + reflexName(reflex)
                    + " error=" + exception.getClass().getSimpleName()
                    + " message=" + exception.getMessage());
        }
    }

    private void tickReflex(Reflex reflex, MinecraftClient client, BotState state, ActionExecutor actions,
                            IntentPlan intentPlan) {
        try {
            if (reflex.isEnabled(state)) {
                reflex.tick(client, state, actions, intentPlan);
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
