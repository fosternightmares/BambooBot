package com.foster.bambooclientbot.sensors;

import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.LookedAtBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class LookingAtBlockSensor {
    public void update(MinecraftClient client, BotState state) {
        if (client == null || client.player == null || client.world == null || !(client.crosshairTarget instanceof BlockHitResult hitResult)) {
            state.setLookedAtBlock(null);
            return;
        }

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            state.setLookedAtBlock(null);
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState blockState = client.world.getBlockState(pos);

        if (blockState.isAir()) {
            state.setLookedAtBlock(null);
            return;
        }

        ClientPlayerEntity player = client.player;
        Vec3d hitPos = hitResult.getPos();
        double distance = Math.sqrt(hitResult.squaredDistanceTo(player));
        String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
        state.setLookedAtBlock(new LookedAtBlock(
                blockId,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                hitResult.getSide().asString(),
                distance,
                hitPos.x,
                hitPos.y,
                hitPos.z
        ));
    }
}
