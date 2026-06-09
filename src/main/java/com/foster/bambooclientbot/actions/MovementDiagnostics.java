package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.behavior.FollowBehavior;
import com.foster.bambooclientbot.control.LookController;
import com.foster.bambooclientbot.logging.BambooBotLog;
import java.util.Locale;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MovementDiagnostics {
    private static final long MOVEMENT_LOG_INTERVAL_MILLIS = 1_000L;
    private static final long FOLLOW_LOG_INTERVAL_MILLIS = 1_000L;
    private static final double BIG_YAW_CHANGE_DEGREES = 45.0;
    private static final double YAW_DISAGREEMENT_DEGREES = 15.0;

    private long lastMovementLogMillis;
    private long lastFollowLogMillis;
    private Float lastSpinSteeringYaw;
    private Float lastSpinLookYaw;
    private Integer previousSpinRouteIndex;
    private Integer lastSpinRouteIndex;
    private int shortRouteRepeatTicks;
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

    public boolean followEmptyRouteFallback() {
        return followEmptyRouteFallback;
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

    public void logMotionSpin(String movementMode, GameOptions options, ClientPlayerEntity player,
                              BlockPos waypoint, Vec3d steeringTarget,
                              LookController.NavigationMotion motion, int routeIndex, int routeLength) {
        if (motion == null) {
            return;
        }

        float steeringChange = lastSpinSteeringYaw == null ? 0.0f : yawDelta(lastSpinSteeringYaw, motion.steeringYaw());
        float lookChange = lastSpinLookYaw == null ? 0.0f : yawDelta(lastSpinLookYaw, motion.lookYaw());
        boolean bigSteeringChange = Math.abs(steeringChange) > BIG_YAW_CHANGE_DEGREES;
        boolean bigLookChange = Math.abs(lookChange) > BIG_YAW_CHANGE_DEGREES;
        boolean yawDisagreement = Math.abs(yawDelta(player.getYaw(), player.getHeadYaw())) > YAW_DISAGREEMENT_DEGREES
                || Math.abs(yawDelta(player.getYaw(), player.getBodyYaw())) > YAW_DISAGREEMENT_DEGREES;
        boolean routeFlip = previousSpinRouteIndex != null
                && lastSpinRouteIndex != null
                && routeIndex == previousSpinRouteIndex
                && routeIndex != lastSpinRouteIndex;
        boolean shortRoute = routeLength <= 2;

        if (shortRoute) {
            shortRouteRepeatTicks++;
        } else {
            shortRouteRepeatTicks = 0;
        }

        BambooBotLog.info(String.format(Locale.ROOT,
                "SPIN m=%s cam=%.2f head=%.2f body=%.2f steerYaw=%.2f steerChange=%.2f steerDist=%.2f steerHeld=%s routeTransition=%s transitionSuppressed=%s lookYaw=%.2f lookChange=%.2f yawDisagree=%s keys=f:%s s:%s j:%s l:%s r:%s b:%s route=%d/%d routeFlip=%s shortRoute=%s shortRepeat=%d waypoint=%s steering=%s lookSrc=%s lookTarget=%s reqLookDy=%.2f appLookDy=%.2f appSteerDy=%.2f bigSteer=%s bigLook=%s",
                movementMode,
                player.getYaw(),
                player.getHeadYaw(),
                player.getBodyYaw(),
                motion.steeringYaw(),
                steeringChange,
                motion.steeringDistance(),
                motion.steeringYawHeld(),
                motion.routeTransition(),
                motion.transitionSteeringSuppressed(),
                motion.lookYaw(),
                lookChange,
                yawDisagreement,
                options.forwardKey.isPressed(),
                options.sprintKey.isPressed(),
                options.jumpKey.isPressed(),
                options.leftKey.isPressed(),
                options.rightKey.isPressed(),
                options.backKey.isPressed(),
                routeIndex,
                routeLength,
                routeFlip,
                shortRoute,
                shortRouteRepeatTicks,
                formatBlock(waypoint),
                formatPosition(steeringTarget),
                motion.lookSource(),
                formatPosition(motion.lookTarget()),
                motion.requestedLookYawDelta(),
                motion.appliedLookYawDelta(),
                motion.appliedSteeringYawDelta(),
                bigSteeringChange,
                bigLookChange
        ));

        previousSpinRouteIndex = lastSpinRouteIndex;
        lastSpinRouteIndex = routeIndex;
        lastSpinSteeringYaw = motion.steeringYaw();
        lastSpinLookYaw = motion.lookYaw();
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

    private float yawDelta(float current, float target) {
        float delta = normalizeYaw(target) - normalizeYaw(current);
        return normalizeYaw(delta);
    }

    private float normalizeYaw(float degrees) {
        float wrapped = degrees % 360.0f;

        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }

        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }

        return wrapped;
    }

    private String formatBlock(BlockPos position) {
        if (position == null) {
            return "none";
        }

        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private String formatPosition(Vec3d position) {
        if (position == null) {
            return "none";
        }

        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", position.x, position.y, position.z);
    }
}
