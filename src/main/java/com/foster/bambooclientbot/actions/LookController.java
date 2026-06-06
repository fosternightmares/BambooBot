package com.foster.bambooclientbot.actions;

import java.util.List;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

class LookController {
    private static final double ROUTE_LOOK_HEIGHT = 1.5;

    void rotateToward(ClientPlayerEntity player, Vec3d targetPosition) {
        Vec3d eyePosition = player.getEyePos();
        double deltaX = targetPosition.x - eyePosition.x;
        double deltaY = targetPosition.y - eyePosition.y;
        double deltaZ = targetPosition.z - eyePosition.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }

    Vec3d routeLookTarget(ClientPlayerEntity player, List<BlockPos> route, int routeIndex) {
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

    Vec3d followRouteLookTarget(List<BlockPos> route, int routeIndex) {
        return waypointCenter(route.get(routeIndex));
    }

    private Vec3d waypointCenter(BlockPos waypoint) {
        return new Vec3d(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5);
    }
}
