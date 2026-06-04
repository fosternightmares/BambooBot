package com.foster.bambooclientbot.navigation;

public record PathPlanResult(boolean found, int nodes, double length, String reason) {
    public static PathPlanResult found(int nodes, double length) {
        return new PathPlanResult(true, nodes, length, "");
    }

    public static PathPlanResult notFound(String reason) {
        return new PathPlanResult(false, 0, 0.0, reason);
    }
}
