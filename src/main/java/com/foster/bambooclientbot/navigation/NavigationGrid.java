package com.foster.bambooclientbot.navigation;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class NavigationGrid {
    public Walkability evaluate(World world, BlockPos position) {
        if (world == null || position == null) {
            return Walkability.BLOCKED;
        }

        BlockPos headPosition = position.up();
        BlockPos supportPosition = position.down();
        BlockState feetState = world.getBlockState(position);
        BlockState headState = world.getBlockState(headPosition);
        BlockState supportState = world.getBlockState(supportPosition);

        if (!feetState.getCollisionShape(world, position).isEmpty()) {
            return Walkability.BLOCKED;
        }

        if (!headState.getCollisionShape(world, headPosition).isEmpty()) {
            return Walkability.BLOCKED;
        }

        if (supportState.getCollisionShape(world, supportPosition).isEmpty()) {
            return Walkability.BLOCKED;
        }

        return Walkability.WALKABLE;
    }
}
