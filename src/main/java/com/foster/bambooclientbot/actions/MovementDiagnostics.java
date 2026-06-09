package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.behavior.FollowBehavior;
import com.foster.bambooclientbot.logging.BambooBotLog;
import java.util.Locale;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;

public class MovementDiagnostics {
    private static final long MOVEMENT_LOG_INTERVAL_MILLIS = 1_000L;
    private static final long FOLLOW_LOG_INTERVAL_MILLIS = 1_000L;

    private long lastMovementLogMillis;
    private long lastFollowLogMillis;
    private String followReplanReason = "none";
    private String followRecovery = "none";
    private int followCandidateCount;
    private int followSuccessfulCandidates;
    private String followSelectedCandidate = "none";
    private boolean followEmptyRouteFallback;

    public void setFollowReplanReason(String reason) {
        followReplanReason = normalize(reason);
    }

    public String followReplanReason(boolean routeEmpty, boolean targetMoved, boolean timerElapsed) {
        if (routeEmpty) {
            return "empty_route";
        }

        if (targetMoved) {
            return "target_moved";
        }

        if (timerElapsed) {
            return "timer";
        }

        return "none";
    }

    public void setFollowRecovery(String recovery) {
        followRecovery = normalize(recovery);
    }

    public void forceNextFollowLog() {
        lastFollowLogMillis = 0L;
    }

    public void recordFollowPlanStats(FollowBehavior.PlanStats stats) {
        followCandidateCount = stats.candidateCount();
        followSuccessfulCandidates = stats.successfulCandidates();
        followSelectedCandidate = stats.selectedCandidate();
        followEmptyRouteFallback = stats.emptyRouteFallback();
    }

    public int followCandidateCount() {
        return followCandidateCount;
    }

    public int followSuccessfulCandidates() {
        return followSuccessfulCandidates;
    }

    public void clearFollowState() {
        followReplanReason = "none";
        followRecovery = "none";
        followCandidateCount = 0;
        followSuccessfulCandidates = 0;
        followSelectedCandidate = "none";
        followEmptyRouteFallback = false;
    }

    public void logMovement(String movementMode, GameOptions options, ClientPlayerEntity player, BlockPos waypoint,
                     double targetDy, boolean jumpWanted, int routeIndex, int routeLength) {
        long now = System.currentTimeMillis();
        if (now - lastMovementLogMillis < MOVEMENT_LOG_INTERVAL_MILLIS) {
            return;
        }

        lastMovementLogMillis = now;
        double waypointDy = waypoint.getY() - player.getY();
        BambooBotLog.info(String.format(
                Locale.ROOT,
                "MOVE m=%s dy=%.1f targetDy=%.1f waypointDy=%.1f pdy=%.1f col=%s ground=%s jumpWanted=%s jump=%s sprint=%s route=%d/%d",
                movementMode,
                targetDy,
                targetDy,
                waypointDy,
                waypointDy,
                player.horizontalCollision,
                player.isOnGround(),
                jumpWanted,
                options.jumpKey.isPressed(),
                options.sprintKey.isPressed(),
                routeIndex,
                routeLength
        ));
    }

    public void logFollow(ClientPlayerEntity player, AbstractClientPlayerEntity target, boolean followGoalSatisfied,
                   String goalLabel, int routeIndex, int routeLength, double followDistance,
                   boolean recoveryActive) {
        long now = System.currentTimeMillis();
        if (now - lastFollowLogMillis < FOLLOW_LOG_INTERVAL_MILLIS) {
            return;
        }

        lastFollowLogMillis = now;
        BambooBotLog.info(String.format(
                Locale.ROOT,
                "FOLLOW dist=%.1f keep=%.1f satisfied=%s goal=%s route=%d/%d replan=%s recovery=%s candidateCount=%d successfulCandidates=%d selectedCandidate=%s emptyRouteFallback=%s",
                player.distanceTo(target),
                followDistance,
                followGoalSatisfied,
                goalLabel,
                routeIndex,
                routeLength,
                followReplanReason,
                followRecovery,
                followCandidateCount,
                followSuccessfulCandidates,
                followSelectedCandidate,
                followEmptyRouteFallback
        ));
        followReplanReason = "none";
        if (!recoveryActive) {
            followRecovery = "none";
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
