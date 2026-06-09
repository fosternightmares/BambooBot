package com.foster.bambooclientbot.sensors;

import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerSnapshot;
import com.foster.bambooclientbot.state.InventorySnapshot;
import com.foster.bambooclientbot.state.LookedAtBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlayerSensors {
    public String readStatus(MinecraftClient client, BotState state) {
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
                + " held=" + heldItem
                + " " + lookingAtBlockStatus(state)
                + " " + state.containerState().format()
                + " " + containerSnapshotStatus(state)
                + " " + inventorySnapshotStatus(state)
                + " activeAction=" + state.activeAction()
                + " followJump=" + state.followJump()
                + " autoSwing=" + state.autoSwing()
                + " autoUse=" + state.autoUse()
                + " autoSneak=" + state.autoSneak()
                + " " + state.autoEatStatus()
                + " " + state.damageStatus()
                + " threatCount=" + state.threatCount()
                + " " + state.routeStatus()
                + " " + state.gotoStatus()
                + " " + state.followRouteStatus()
                + " " + state.recentRouteStatus()
                + " " + state.lastActionStatus()
                + " " + state.lastTransferStatus()
                + " " + state.lastDropStatus();
    }

    private String containerSnapshotStatus(BotState state) {
        ContainerSnapshot snapshot = state.containerSnapshot();

        if (snapshot == null) {
            return "containerSnapshotSlots=0 containerSnapshotOccupied=0";
        }

        return "containerSnapshotSlots=" + snapshot.slotCount()
                + " containerSnapshotOccupied=" + snapshot.occupiedSlots();
    }

    private String inventorySnapshotStatus(BotState state) {
        InventorySnapshot snapshot = state.inventorySnapshot();

        if (snapshot == null) {
            return "inventorySlots=0 inventoryOccupied=0";
        }

        return "inventorySlots=" + snapshot.slotCount()
                + " inventoryOccupied=" + snapshot.occupiedSlots();
    }

    private String lookingAtBlockStatus(BotState state) {
        LookedAtBlock lookedAtBlock = state.lookedAtBlock();

        if (lookedAtBlock == null) {
            return "lookingAtBlock=none";
        }

        return lookedAtBlock.format();
    }

    private String heldItemName(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }

        return Registries.ITEM.getId(stack.getItem()).getPath();
    }
}
