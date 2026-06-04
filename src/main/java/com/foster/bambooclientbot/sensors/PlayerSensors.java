package com.foster.bambooclientbot.sensors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlayerSensors {
    public String readStatus(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return "status unavailable";
        }

        ClientPlayerEntity player = client.player;
        World world = client.world;
        BlockPos pos = player.getBlockPos();
        String dimension = world.getRegistryKey().getValue().toString();
        String heldItem = heldItemName(player.getMainHandStack());

        return "pos=" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " dim=" + dimension
                + " hp=" + player.getHealth()
                + " food=" + player.getHungerManager().getFoodLevel()
                + " held=" + heldItem;
    }

    private String heldItemName(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }

        return Registries.ITEM.getId(stack.getItem()).getPath();
    }
}
