package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerSnapshot;
import com.foster.bambooclientbot.state.LookedAtBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

class ContainerActionExecutor {
    private final BotState state;
    private final InventoryActionExecutor inventoryActionExecutor;

    ContainerActionExecutor(BotState state, InventoryActionExecutor inventoryActionExecutor) {
        this.state = state;
        this.inventoryActionExecutor = inventoryActionExecutor;
    }

    void captureContainerSnapshot(MinecraftClient client, ActionRequest request) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "container_not_open");
            state.queueChatMessage("container not open");
            return;
        }

        List<ContainerSnapshot.Entry> entries = new ArrayList<>();
        List<Slot> slots = inventoryActionExecutor.containerSlots(handledScreen.getScreenHandler().slots);

        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            Slot slot = slots.get(slotIndex);
            ItemStack stack = slot.getStack();

            if (stack.isEmpty()) {
                continue;
            }

            entries.add(new ContainerSnapshot.Entry(
                    slotIndex,
                    Registries.ITEM.getId(stack.getItem()).toString(),
                    stack.getCount()
            ));
        }

        state.setContainerSnapshot(new ContainerSnapshot(System.currentTimeMillis(), slots.size(), List.copyOf(entries)));
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage("snapshot captured");
    }

    void interactTargetedBlock(MinecraftClient client, ActionRequest request) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "client_unavailable");
            state.queueChatMessage("action failed");
            return;
        }

        LookedAtBlock target = request.targetedBlock();

        if (target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "no_target_block");
            state.queueChatMessage("action failed");
            return;
        }

        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());

        if (client.world.getBlockState(pos).isAir()) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "no_target_block");
            state.queueChatMessage("action failed");
            return;
        }

        Direction side = Direction.valueOf(target.side().toUpperCase(Locale.ROOT));
        Vec3d hitPos = new Vec3d(target.hitX(), target.hitY(), target.hitZ());
        BlockHitResult hitResult = new BlockHitResult(hitPos, side, pos, false);
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);

        if (result.isAccepted()) {
            client.player.swingHand(Hand.MAIN_HAND);
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.setLastActionResult(request.actionType().name(), "success", "");
            state.queueChatMessage("interacted");
        } else {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "not_accepted");
            state.queueChatMessage("action failed");
        }
    }

    void closeCurrentScreen(MinecraftClient client, ActionRequest request) {
        if (client == null || client.player == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "client_unavailable");
            state.queueChatMessage("action failed");
            return;
        }

        if (client.currentScreen == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "no_screen_open");
            state.queueChatMessage("nothing_to_close");
            return;
        }

        client.player.closeScreen();
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage("closed");
    }
}
