package com.foster.bambooclientbot.intent;

import java.util.EnumSet;
import java.util.Set;

public class IntentPlan {
    private final Set<BotIntentType> approvedIntents;

    public IntentPlan(Set<BotIntentType> approvedIntents) {
        this.approvedIntents = approvedIntents.isEmpty()
                ? EnumSet.noneOf(BotIntentType.class)
                : EnumSet.copyOf(approvedIntents);
    }

    public static IntentPlan none() {
        return new IntentPlan(EnumSet.noneOf(BotIntentType.class));
    }

    public boolean isApproved(BotIntentType type) {
        return approvedIntents.contains(type);
    }
}
