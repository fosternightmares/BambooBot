package com.foster.bambooclientbot.control;

import com.foster.bambooclientbot.logging.BambooBotLog;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class LookController {
    private static final double ROUTE_LOOK_HEIGHT = 1.5;
    private static final int ROUTE_GAZE_PREVIEW_OFFSET = 3;
    private static final int ROUTE_STEERING_LOOKAHEAD_OFFSET = 2;
    private static final double ROUTE_STEERING_LOOKAHEAD_DISTANCE = 1.75;
    private static final double GAZE_TARGET_TINY_MOVE_DISTANCE = 0.35;
    private static final double GAZE_TARGET_MEANINGFUL_MOVE_DISTANCE = 1.25;
    private static final long GAZE_TARGET_MIN_HOLD_MILLIS = 250L;
    private static final double MAX_GAZE_YAW_DELTA = 6.0;
    private static final double MAX_GAZE_PITCH_DELTA = 4.0;
    private static final double MAX_NAVIGATION_TARGET_YAW_CHANGE = 40.0;

    private Vec3d stableGazeTarget;
    private long lastGazeTargetUpdateMillis;
    private Float lastNavigationSteeringYaw;
    private int lookRequestCount;
    private boolean lookConflictLogged;
    private String lastWriteMethod = "none";
    private int lastYawDeltaSign;
    private int lastPitchDeltaSign;

    public void beginTick() {
        lookRequestCount = 0;
        lookConflictLogged = false;
    }

    public void rotateToward(ClientPlayerEntity player, Vec3d targetPosition) {
        recordLookRequest("direct");
        Angles angles = anglesTo(player, targetPosition);
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();
        float appliedYawDelta = wrapDegrees(angles.yaw() - currentYaw);
        float appliedPitchDelta = angles.pitch() - currentPitch;

        stableGazeTarget = targetPosition;
        lastGazeTargetUpdateMillis = System.currentTimeMillis();
        player.setYaw(angles.yaw());
        player.setPitch(angles.pitch());
        player.setHeadYaw(angles.yaw());
        player.setBodyYaw(angles.yaw());
        logRotationWrite("rotateToward", currentYaw, currentPitch, angles.yaw(), angles.pitch(),
                appliedYawDelta, appliedPitchDelta, player);
    }

    public void rotateForNavigation(ClientPlayerEntity player, Vec3d steeringTarget, Vec3d gazeTarget) {
        recordLookRequest("navigation");
        Angles steeringAngles = anglesTo(player, steeringTarget);
        Vec3d stabilizedGazeTarget = stabilizedNavigationGazeTarget(player, gazeTarget, steeringAngles.yaw());
        Angles gazeAngles = anglesTo(player, stabilizedGazeTarget);
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();
        float headYaw = approachAngle(player.getHeadYaw(), gazeAngles.yaw(), MAX_GAZE_YAW_DELTA);
        float pitch = approachAngle(player.getPitch(), gazeAngles.pitch(), MAX_GAZE_PITCH_DELTA);
        float appliedYawDelta = wrapDegrees(steeringAngles.yaw() - currentYaw);
        float appliedPitchDelta = pitch - currentPitch;

        player.setYaw(steeringAngles.yaw());
        player.setPitch(pitch);
        player.setHeadYaw(headYaw);
        player.setBodyYaw(steeringAngles.yaw());
        logRotationWrite("rotateForNavigation", currentYaw, currentPitch, steeringAngles.yaw(), gazeAngles.pitch(),
                appliedYawDelta, appliedPitchDelta, player);
    }

    public void clearNavigationGaze() {
        stableGazeTarget = null;
        lastGazeTargetUpdateMillis = 0L;
        lastNavigationSteeringYaw = null;
    }

    public Vec3d routeLookTarget(ClientPlayerEntity player, List<BlockPos> route, int routeIndex) {
        BlockPos waypoint = route.get(routeIndex);
        Vec3d current = waypointCenter(waypoint);
        Vec3d lookPoint = current;

        if (routeIndex + 1 < route.size()) {
            Vec3d next = waypointCenter(route.get(routeIndex + 1));
            lookPoint = current.add(next).multiply(0.5);
        }

        double lookY = Math.max(player.getEyeY() - 0.2, waypoint.getY() + ROUTE_LOOK_HEIGHT);
        return new Vec3d(lookPoint.x, lookY, lookPoint.z);
    }

    public Vec3d followRouteLookTarget(List<BlockPos> route, int routeIndex) {
        return waypointCenter(route.get(routeIndex));
    }

    public Vec3d routeSteeringTarget(ClientPlayerEntity player, List<BlockPos> route, int routeIndex) {
        int steeringIndex = routeIndex;

        if (horizontalDistance(player, route.get(routeIndex)) <= ROUTE_STEERING_LOOKAHEAD_DISTANCE) {
            if (canSteerAhead(route, routeIndex, ROUTE_STEERING_LOOKAHEAD_OFFSET)) {
                steeringIndex = routeIndex + ROUTE_STEERING_LOOKAHEAD_OFFSET;
            } else if (canSteerAhead(route, routeIndex, 1)) {
                steeringIndex = routeIndex + 1;
            }
        }

        BlockPos steeringWaypoint = route.get(steeringIndex);
        Vec3d steeringCenter = waypointCenter(steeringWaypoint);
        double lookY = Math.max(player.getEyeY() - 0.2, steeringWaypoint.getY() + ROUTE_LOOK_HEIGHT);
        return new Vec3d(steeringCenter.x, lookY, steeringCenter.z);
    }

    public Vec3d routePreviewGazeTarget(ClientPlayerEntity player, List<BlockPos> route, int routeIndex) {
        int previewIndex = routeIndex;

        if (routeIndex + ROUTE_GAZE_PREVIEW_OFFSET < route.size()) {
            previewIndex = routeIndex + ROUTE_GAZE_PREVIEW_OFFSET;
        } else if (routeIndex + 2 < route.size()) {
            previewIndex = routeIndex + 2;
        } else if (routeIndex + 1 < route.size()) {
            previewIndex = routeIndex + 1;
        }

        BlockPos preview = route.get(previewIndex);
        Vec3d previewCenter = waypointCenter(preview);
        double lookY = Math.max(player.getEyeY() - 0.2, preview.getY() + ROUTE_LOOK_HEIGHT);
        return new Vec3d(previewCenter.x, lookY, previewCenter.z);
    }

    public Vec3d upperBodyTarget(AbstractClientPlayerEntity target) {
        double y = target.getY() + target.getHeight() * 0.75;
        return new Vec3d(target.getX(), y, target.getZ());
    }

    private Vec3d waypointCenter(BlockPos waypoint) {
        return new Vec3d(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5);
    }

    private boolean canSteerAhead(List<BlockPos> route, int routeIndex, int offset) {
        if (routeIndex + offset >= route.size()) {
            return false;
        }

        int routeY = route.get(routeIndex).getY();

        for (int index = routeIndex + 1; index <= routeIndex + offset; index++) {
            if (route.get(index).getY() != routeY) {
                return false;
            }
        }

        return true;
    }

    private double horizontalDistance(ClientPlayerEntity player, BlockPos target) {
        double deltaX = target.getX() + 0.5 - player.getX();
        double deltaZ = target.getZ() + 0.5 - player.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private Vec3d stabilizedNavigationGazeTarget(ClientPlayerEntity player, Vec3d requestedTarget, float steeringYaw) {
        long now = System.currentTimeMillis();

        if (stableGazeTarget == null) {
            stableGazeTarget = requestedTarget;
            lastGazeTargetUpdateMillis = now;
            lastNavigationSteeringYaw = steeringYaw;
            return stableGazeTarget;
        }

        Angles requestedAngles = anglesTo(player, requestedTarget);
        Angles stableAngles = anglesTo(player, stableGazeTarget);
        double requestedYawChange = Math.abs(wrapDegrees(requestedAngles.yaw() - stableAngles.yaw()));
        double steeringYawChange = lastNavigationSteeringYaw == null
                ? 0.0
                : Math.abs(wrapDegrees(steeringYaw - lastNavigationSteeringYaw));

        if (requestedYawChange > MAX_NAVIGATION_TARGET_YAW_CHANGE
                && steeringYawChange <= MAX_NAVIGATION_TARGET_YAW_CHANGE) {
            lastNavigationSteeringYaw = steeringYaw;
            return stableGazeTarget;
        }

        double squaredDistance = stableGazeTarget.squaredDistanceTo(requestedTarget);
        double tinyMoveSquared = GAZE_TARGET_TINY_MOVE_DISTANCE * GAZE_TARGET_TINY_MOVE_DISTANCE;
        double meaningfulMoveSquared = GAZE_TARGET_MEANINGFUL_MOVE_DISTANCE * GAZE_TARGET_MEANINGFUL_MOVE_DISTANCE;
        boolean holdExpired = now - lastGazeTargetUpdateMillis >= GAZE_TARGET_MIN_HOLD_MILLIS;

        if (squaredDistance >= meaningfulMoveSquared || (holdExpired && squaredDistance >= tinyMoveSquared)) {
            stableGazeTarget = requestedTarget;
            lastGazeTargetUpdateMillis = now;
        }

        lastNavigationSteeringYaw = steeringYaw;
        return stableGazeTarget;
    }

    private Angles anglesTo(ClientPlayerEntity player, Vec3d targetPosition) {
        Vec3d eyePosition = player.getEyePos();
        double deltaX = targetPosition.x - eyePosition.x;
        double deltaY = targetPosition.y - eyePosition.y;
        double deltaZ = targetPosition.z - eyePosition.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));
        return new Angles(yaw, pitch);
    }

    private float approachAngle(float current, float target, double maxDelta) {
        float delta = wrapDegrees(target - current);

        if (delta > maxDelta) {
            delta = (float) maxDelta;
        } else if (delta < -maxDelta) {
            delta = (float) -maxDelta;
        }

        return current + delta;
    }

    private float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;

        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }

        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }

        return wrapped;
    }

    private void recordLookRequest(String source) {
        lookRequestCount++;

        if (lookRequestCount > 1 && !lookConflictLogged) {
            lookConflictLogged = true;
            BambooBotLog.warn("LOOK_CONFLICT count=" + lookRequestCount + " source=" + source);
        }
    }

    private void logRotationWrite(String method, float currentYaw, float currentPitch, float targetYaw,
                                  float targetPitch, float appliedYawDelta, float appliedPitchDelta,
                                  ClientPlayerEntity player) {
        int yawDeltaSign = sign(appliedYawDelta);
        int pitchDeltaSign = sign(appliedPitchDelta);
        boolean yawSignFlip = lastYawDeltaSign != 0 && yawDeltaSign != 0 && yawDeltaSign != lastYawDeltaSign;
        boolean pitchSignFlip = lastPitchDeltaSign != 0 && pitchDeltaSign != 0
                && pitchDeltaSign != lastPitchDeltaSign;
        boolean methodSwitch = !lastWriteMethod.equals("none") && !lastWriteMethod.equals(method);
        boolean headYawDiffers = Math.abs(wrapDegrees(player.getHeadYaw() - player.getYaw())) > 0.01f;
        boolean bodyYawDiffers = Math.abs(wrapDegrees(player.getBodyYaw() - player.getYaw())) > 0.01f;

        BambooBotLog.info(String.format(Locale.ROOT,
                "LOOK m=%s switch=%s cy=%.2f cp=%.2f ty=%.2f tp=%.2f dy=%.2f dp=%.2f yflip=%s pflip=%s headDiff=%s bodyDiff=%s",
                method,
                methodSwitch,
                currentYaw,
                currentPitch,
                targetYaw,
                targetPitch,
                appliedYawDelta,
                appliedPitchDelta,
                yawSignFlip,
                pitchSignFlip,
                headYawDiffers,
                bodyYawDiffers));

        lastWriteMethod = method;
        if (yawDeltaSign != 0) {
            lastYawDeltaSign = yawDeltaSign;
        }
        if (pitchDeltaSign != 0) {
            lastPitchDeltaSign = pitchDeltaSign;
        }
    }

    private int sign(float value) {
        if (Math.abs(value) < 0.01f) {
            return 0;
        }

        return value > 0.0f ? 1 : -1;
    }

    private record Angles(float yaw, float pitch) {
    }
}
