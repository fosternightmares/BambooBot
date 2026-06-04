package com.foster.bambooclientbot.navigation;

import java.util.List;
import net.minecraft.util.math.BlockPos;

public record PathPlanResult(boolean found, int nodes, double length, String reason, List<BlockPos> path) {
    public static PathPlanResult found(List<BlockPos> path, double length) {
        return new PathPlanResult(true, path.size(), length, "", List.copyOf(path));
    }

    public static PathPlanResult notFound(String reason) {
        return new PathPlanResult(false, 0, 0.0, reason, List.of());
    }
}
