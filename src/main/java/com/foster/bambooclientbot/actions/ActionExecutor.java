package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.commands.ItemResolver;
import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerSnapshot;
import com.foster.bambooclientbot.state.InventorySnapshot;
import com.foster.bambooclientbot.state.LookedAtBlock;
import com.foster.bambooclientbot.state.TransferResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ActionExecutor {
    private static final double LOOK_AT_RANGE = 32.0;
    private static final double APPROACH_TARGET_DISTANCE = 2.5;
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double FOLLOW_SPRINT_ENABLE_DISTANCE = 6.0;
    private static final double FOLLOW_SPRINT_DISABLE_DISTANCE = 4.0;
    private static final int MAIN_INVENTORY_SLOT_COUNT = 36;
    private static final int INVENTORY_DETAILS_MAX_LENGTH = 240;

    private final BotState state;
    private ActionRequest timedMovement;
    private int timedMovementTicksRemaining;
    private ActionRequest activeApproach;
    private ActionRequest activeFollow;

    public ActionExecutor(BotState state) {
        this.state = state;
    }

    public void tick(MinecraftClient client) {
        ActionRequest request = state.pollAction();

        if (request != null) {
            executeAction(client, request);
            return;
        }

        tickApproach(client);
        tickFollow(client);
        tickTimedMovement(client);
    }

    private void executeAction(MinecraftClient client, ActionRequest request) {
        request.setStatus(ActionRequest.ActionStatus.RUNNING);
        state.setActiveAction(request.actionType().name());

        try {
            if (request.actionType() == ActionRequest.ActionType.STOP) {
                activeApproach = null;
                activeFollow = null;
                timedMovement = null;
                timedMovementTicksRemaining = 0;
                stop(client);
                request.setStatus(ActionRequest.ActionStatus.COMPLETE);
                state.queueChatMessage("stopped");
            } else if (request.actionType() == ActionRequest.ActionType.FORWARD) {
                move(client, request, "forward");
            } else if (request.actionType() == ActionRequest.ActionType.BACK) {
                move(client, request, "back");
            } else if (request.actionType() == ActionRequest.ActionType.LEFT) {
                move(client, request, "left");
            } else if (request.actionType() == ActionRequest.ActionType.RIGHT) {
                move(client, request, "right");
            } else if (request.actionType() == ActionRequest.ActionType.LOOK_AT_PLAYER) {
                if (lookAtPlayer(client, request.actionData())) {
                    request.setStatus(ActionRequest.ActionStatus.COMPLETE);
                    state.queueChatMessage("looking at " + request.actionData());
                } else {
                    request.setStatus(ActionRequest.ActionStatus.FAILED);
                    state.queueChatMessage("player not found");
                }
            } else if (request.actionType() == ActionRequest.ActionType.APPROACH_PLAYER) {
                startApproach(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.FOLLOW_PLAYER) {
                startFollow(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.INTERACT_TARGETED_BLOCK) {
                interactTargetedBlock(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.CLOSE_CURRENT_SCREEN) {
                closeCurrentScreen(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.CAPTURE_CONTAINER_SNAPSHOT) {
                captureContainerSnapshot(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.DEPOSIT_ITEM) {
                transferItems(client, request, "deposit", true);
            } else if (request.actionType() == ActionRequest.ActionType.WITHDRAW_ITEM) {
                transferItems(client, request, "withdraw", false);
            } else if (request.actionType() == ActionRequest.ActionType.CAPTURE_INVENTORY_SNAPSHOT) {
                captureInventorySnapshot(client, request, false);
            } else if (request.actionType() == ActionRequest.ActionType.CAPTURE_INVENTORY_DETAILS) {
                captureInventorySnapshot(client, request, true);
            }
        } catch (Exception exception) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "exception");
            state.queueChatMessage("action failed");
        } finally {
            state.clearActiveAction();
        }
    }

    private void captureContainerSnapshot(MinecraftClient client, ActionRequest request) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "container_not_open");
            state.queueChatMessage("container not open");
            return;
        }

        List<ContainerSnapshot.Entry> entries = new ArrayList<>();
        List<Slot> slots = containerSlots(handledScreen.getScreenHandler().slots);

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

    private void captureInventorySnapshot(MinecraftClient client, ActionRequest request, boolean includeDetails) {
        if (client == null || client.player == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "inventory_unavailable");
            state.queueChatMessage("inventory_unavailable");
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        List<InventorySnapshot.Entry> entries = new ArrayList<>();
        int slotCount = Math.min(MAIN_INVENTORY_SLOT_COUNT, inventory.size());

        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = inventory.getStack(slot);

            if (stack.isEmpty()) {
                continue;
            }

            entries.add(new InventorySnapshot.Entry(
                    slot,
                    Registries.ITEM.getId(stack.getItem()).toString(),
                    stack.getCount()
            ));
        }

        InventorySnapshot snapshot = new InventorySnapshot(System.currentTimeMillis(), slotCount, List.copyOf(entries));
        state.setInventorySnapshot(snapshot);
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");

        if (includeDetails) {
            state.queueChatMessage(formatInventoryDetails(snapshot));
        } else {
            state.queueChatMessage("inventory snapshot captured");
        }
    }

    private String formatInventoryDetails(InventorySnapshot snapshot) {
        String prefix = "inventory slots=" + snapshot.slotCount() + " occupied=" + snapshot.occupiedSlots() + " [";
        StringBuilder message = new StringBuilder(prefix);
        int includedEntries = 0;

        for (InventorySnapshot.Entry entry : snapshot.entries()) {
            String formattedEntry = formatInventoryEntry(entry);
            int separatorLength = includedEntries == 0 ? 0 : 1;
            int remainingAfterEntry = snapshot.occupiedSlots() - includedEntries - 1;
            String truncationSuffix = remainingAfterEntry > 0 ? ",...+" + remainingAfterEntry : "";
            int suffixLength = truncationSuffix.length() + 1;

            if (message.length() + separatorLength + formattedEntry.length() + suffixLength > INVENTORY_DETAILS_MAX_LENGTH) {
                break;
            }

            if (includedEntries > 0) {
                message.append(',');
            }

            message.append(formattedEntry);
            includedEntries++;
        }

        if (includedEntries < snapshot.occupiedSlots()) {
            if (includedEntries > 0) {
                message.append(',');
            }

            message.append("...+").append(snapshot.occupiedSlots() - includedEntries);
        }

        message.append(']');
        return message.toString();
    }

    private String formatInventoryEntry(InventorySnapshot.Entry entry) {
        return entry.slot() + ":" + compactItemId(entry.itemId()) + "x" + entry.count();
    }

    private void transferItems(MinecraftClient client, ActionRequest request, String operation, boolean deposit) {
        String normalizedItemId = normalizeItemId(request.itemId());

        if (normalizedItemId.isBlank() || request.requestedCount() <= 0) {
            failTransfer(request, operation, normalizedItemId, 0, true, 0, 0, "invalid_item");
            return;
        }

        if (client == null || client.player == null || client.interactionManager == null
                || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            failTransfer(request, operation, normalizedItemId, 0, true, 0, 0, "container_not_open");
            return;
        }

        ScreenHandler handler = handledScreen.getScreenHandler();

        if (!handler.getCursorStack().isEmpty()) {
            failTransfer(request, operation, normalizedItemId, 0, true, 0, 0, "transfer_failed");
            return;
        }

        List<Slot> sourceSlots = transferSlots(handler.slots, deposit);
        List<Slot> targetSlots = transferSlots(handler.slots, !deposit);
        int sourceCountBefore = countMatchingItems(sourceSlots, normalizedItemId);
        int targetCountBefore = countMatchingItems(targetSlots, normalizedItemId);
        int available = countMatchingItems(sourceSlots, normalizedItemId);

        if (available <= 0) {
            failTransfer(request, operation, normalizedItemId, 0, true, 0, 0, "item_not_found");
            return;
        }

        int capacity = countTargetCapacity(targetSlots, normalizedItemId);

        if (capacity <= 0) {
            failTransfer(request, operation, normalizedItemId, 0, true, 0, 0, transferFullReason(deposit));
            return;
        }

        int desiredCount = Math.min(request.requestedCount(), Math.min(available, capacity));
        int movedCount = moveExactCount(client, handler, sourceSlots, targetSlots, normalizedItemId, desiredCount);
        int sourceCountAfter = countMatchingItems(sourceSlots, normalizedItemId);
        int targetCountAfter = countMatchingItems(targetSlots, normalizedItemId);
        int sourceDelta = sourceCountBefore - sourceCountAfter;
        int targetDelta = targetCountAfter - targetCountBefore;
        int actualMoved = Math.min(sourceDelta, targetDelta);

        if (movedCount <= 0) {
            failTransfer(request, operation, normalizedItemId, 0, true, movedCount, Math.max(0, actualMoved), "transfer_failed");
            return;
        }

        if (!handler.getCursorStack().isEmpty()) {
            failTransfer(request, operation, normalizedItemId, movedCount, false, movedCount, Math.max(0, actualMoved), "transfer_mismatch");
            return;
        }

        if (sourceDelta != movedCount || targetDelta != movedCount) {
            failTransfer(request, operation, normalizedItemId, movedCount, false, movedCount, Math.max(0, actualMoved), "transfer_mismatch");
            return;
        }

        String result = movedCount == request.requestedCount() ? "success" : "partial";
        String reason = partialReason(movedCount, request.requestedCount(), available, capacity, deposit);
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), result, reason);
        state.setLastTransferResult(new TransferResult(
                operation,
                normalizedItemId,
                request.requestedCount(),
                movedCount,
                true,
                movedCount,
                movedCount,
                result,
                reason
        ));
        state.queueChatMessage("success".equals(result) ? "transfer verified" : "transfer partial");
    }

    private void failTransfer(ActionRequest request, String operation, String itemId, int movedCount,
                              boolean verified, int expectedMoved, int actualMoved, String reason) {
        request.setStatus(ActionRequest.ActionStatus.FAILED);
        state.setLastActionResult(request.actionType().name(), "failed", reason);
        state.setLastTransferResult(new TransferResult(
                operation,
                itemId,
                request.requestedCount(),
                movedCount,
                verified,
                expectedMoved,
                actualMoved,
                "failed",
                reason
        ));
        state.queueChatMessage("transfer failed");
    }

    private String partialReason(int movedCount, int requestedCount, int available, int capacity, boolean deposit) {
        if (movedCount >= requestedCount) {
            return "";
        }

        if (available < requestedCount) {
            return "insufficient_quantity";
        }

        if (capacity < requestedCount) {
            return transferFullReason(deposit);
        }

        return "";
    }

    private String transferFullReason(boolean deposit) {
        return deposit ? "container_full" : "inventory_full";
    }

    private List<Slot> transferSlots(List<Slot> slots, boolean playerInventorySlots) {
        List<Slot> matchingSlots = new ArrayList<>();

        for (Slot slot : slots) {
            if (isPlayerInventorySlot(slot) == playerInventorySlots) {
                matchingSlots.add(slot);
            }
        }

        return matchingSlots;
    }

    private List<Slot> containerSlots(List<Slot> slots) {
        List<Slot> matchingSlots = new ArrayList<>();

        for (Slot slot : slots) {
            if (!isPlayerInventorySlot(slot)) {
                matchingSlots.add(slot);
            }
        }

        return matchingSlots;
    }

    private boolean isPlayerInventorySlot(Slot slot) {
        return slot.inventory instanceof PlayerInventory;
    }

    private int countMatchingItems(List<Slot> slots, String itemId) {
        int count = 0;

        for (Slot slot : slots) {
            ItemStack stack = slot.getStack();

            if (!stack.isEmpty() && matchesItem(stack, itemId)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private int countTargetCapacity(List<Slot> slots, String itemId) {
        int capacity = 0;

        for (Slot slot : slots) {
            ItemStack stack = slot.getStack();

            if (stack.isEmpty()) {
                capacity += slot.getMaxItemCount();
            } else if (matchesItem(stack, itemId)) {
                capacity += Math.max(0, slot.getMaxItemCount(stack) - stack.getCount());
            }
        }

        return capacity;
    }

    private int moveExactCount(MinecraftClient client, ScreenHandler handler, List<Slot> sourceSlots,
                               List<Slot> targetSlots, String itemId, int desiredCount) {
        int movedCount = 0;

        for (Slot sourceSlot : sourceSlots) {
            if (movedCount >= desiredCount) {
                break;
            }

            ItemStack sourceStack = sourceSlot.getStack();

            if (sourceStack.isEmpty() || !matchesItem(sourceStack, itemId) || !sourceSlot.canTakeItems(client.player)) {
                continue;
            }

            int toMoveFromSlot = Math.min(sourceStack.getCount(), desiredCount - movedCount);
            clickSlot(client, handler, sourceSlot, 0);

            int placedFromSlot = 0;

            while (placedFromSlot < toMoveFromSlot && !handler.getCursorStack().isEmpty()) {
                Slot targetSlot = findTargetSlotForOne(targetSlots, handler.getCursorStack());

                if (targetSlot == null) {
                    break;
                }

                clickSlot(client, handler, targetSlot, 1);
                placedFromSlot++;
            }

            movedCount += placedFromSlot;

            if (!handler.getCursorStack().isEmpty()) {
                clickSlot(client, handler, sourceSlot, 0);
            }

            if (!handler.getCursorStack().isEmpty()) {
                break;
            }
        }

        return movedCount;
    }

    private Slot findTargetSlotForOne(List<Slot> targetSlots, ItemStack cursorStack) {
        for (Slot targetSlot : targetSlots) {
            if (canAcceptOne(targetSlot, cursorStack)) {
                return targetSlot;
            }
        }

        return null;
    }

    private boolean canAcceptOne(Slot targetSlot, ItemStack cursorStack) {
        if (cursorStack.isEmpty() || !targetSlot.canInsert(cursorStack)) {
            return false;
        }

        ItemStack targetStack = targetSlot.getStack();

        if (targetStack.isEmpty()) {
            return true;
        }

        return targetStack.getItem() == cursorStack.getItem()
                && targetStack.getCount() < targetSlot.getMaxItemCount(cursorStack);
    }

    private void clickSlot(MinecraftClient client, ScreenHandler handler, Slot slot, int button) {
        client.interactionManager.clickSlot(handler.syncId, slot.id, button, SlotActionType.PICKUP, client.player);
    }

    private boolean matchesItem(ItemStack stack, String itemId) {
        String stackItemId = Registries.ITEM.getId(stack.getItem()).toString();
        return stackItemId.equals(itemId);
    }

    private String normalizeItemId(String itemId) {
        return ItemResolver.resolveItemArg(itemId);
    }

    private String compactItemId(String itemId) {
        if (itemId.startsWith("minecraft:")) {
            return itemId.substring("minecraft:".length());
        }

        return itemId;
    }

    private void interactTargetedBlock(MinecraftClient client, ActionRequest request) {
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

    private void closeCurrentScreen(MinecraftClient client, ActionRequest request) {
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

    private void tickApproach(MinecraftClient client) {
        if (activeApproach == null) {
            return;
        }

        if (client == null || client.player == null || client.world == null || client.options == null) {
            activeApproach.setStatus(ActionRequest.ActionStatus.FAILED);
            activeApproach = null;
            stop(client);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, activeApproach.actionData());

        if (target == null) {
            activeApproach.setStatus(ActionRequest.ActionStatus.FAILED);
            activeApproach = null;
            stop(client);
            state.queueChatMessage("player not found");
            return;
        }

        if (player.distanceTo(target) <= APPROACH_TARGET_DISTANCE) {
            stop(client);
            activeApproach.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.queueChatMessage("arrived near " + activeApproach.actionData());
            activeApproach = null;
            return;
        }

        rotateToward(player, target.getEyePos());
        client.options.forwardKey.setPressed(true);
    }

    private void tickFollow(MinecraftClient client) {
        if (activeFollow == null) {
            return;
        }

        if (client == null || client.player == null || client.world == null || client.options == null) {
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            stop(client);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, activeFollow.actionData());

        if (target == null) {
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            stop(client);
            state.queueChatMessage("player not found");
            return;
        }

        rotateToward(player, target.getEyePos());

        if (player.distanceTo(target) > FOLLOW_DISTANCE) {
            clearDirectionalKeys(client.options);
            client.options.forwardKey.setPressed(true);
        } else {
            clearDirectionalKeys(client.options);
        }

        updateFollowSprint(client.options, player.distanceTo(target));
    }

    private void tickTimedMovement(MinecraftClient client) {
        if (timedMovement == null) {
            return;
        }

        if (client == null || client.player == null || client.options == null) {
            timedMovement.setStatus(ActionRequest.ActionStatus.FAILED);
            timedMovement = null;
            timedMovementTicksRemaining = 0;
            state.queueChatMessage("action failed");
            return;
        }

        timedMovementTicksRemaining--;

        if (timedMovementTicksRemaining <= 0) {
            stop(client);
            timedMovement.setStatus(ActionRequest.ActionStatus.COMPLETE);
            timedMovement = null;
            state.queueChatMessage("movement complete");
        }
    }

    private void move(MinecraftClient client, ActionRequest request, String direction) {
        if (client == null || client.player == null || client.options == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
            return;
        }

        activeApproach = null;
        activeFollow = null;
        GameOptions options = client.options;
        clearDirectionalKeys(options);
        options.sprintKey.setPressed(false);
        movementKey(options, request.actionType()).setPressed(true);

        if (request.durationTicks() > 0) {
            timedMovement = request;
            timedMovementTicksRemaining = request.durationTicks();
            state.queueChatMessage("moving " + direction + " for " + request.durationSeconds() + "s");
        } else {
            timedMovement = null;
            timedMovementTicksRemaining = 0;
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.queueChatMessage("moving " + direction);
        }
    }

    private void startApproach(MinecraftClient client, ActionRequest request) {
        if (client == null || client.player == null || client.world == null || client.options == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, request.actionData());

        if (target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("player not found");
            return;
        }

        if (player.distanceTo(target) <= APPROACH_TARGET_DISTANCE) {
            stop(client);
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.queueChatMessage("arrived near " + request.actionData());
            return;
        }

        timedMovement = null;
        timedMovementTicksRemaining = 0;
        activeFollow = null;
        clearDirectionalKeys(client.options);
        client.options.sprintKey.setPressed(false);
        rotateToward(player, target.getEyePos());
        client.options.forwardKey.setPressed(true);
        activeApproach = request;
        state.queueChatMessage("approaching " + request.actionData());
    }

    private void startFollow(MinecraftClient client, ActionRequest request) {
        if (client == null || client.player == null || client.world == null || client.options == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, request.actionData());

        if (target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.queueChatMessage("player not found");
            return;
        }

        timedMovement = null;
        timedMovementTicksRemaining = 0;
        activeApproach = null;
        clearDirectionalKeys(client.options);
        client.options.sprintKey.setPressed(false);
        rotateToward(player, target.getEyePos());

        if (player.distanceTo(target) > FOLLOW_DISTANCE) {
            client.options.forwardKey.setPressed(true);
        }

        updateFollowSprint(client.options, player.distanceTo(target));
        activeFollow = request;
        state.queueChatMessage("following " + request.actionData());
    }

    private void stop(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        GameOptions options = client.options;
        clearDirectionalKeys(options);
        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }

    private void updateFollowSprint(GameOptions options, double targetDistance) {
        if (targetDistance > FOLLOW_SPRINT_ENABLE_DISTANCE) {
            options.sprintKey.setPressed(true);
        } else if (targetDistance <= FOLLOW_SPRINT_DISABLE_DISTANCE) {
            options.sprintKey.setPressed(false);
        }
    }

    private void clearDirectionalKeys(GameOptions options) {
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
    }

    private KeyBinding movementKey(GameOptions options, ActionRequest.ActionType actionType) {
        return switch (actionType) {
            case FORWARD -> options.forwardKey;
            case BACK -> options.backKey;
            case LEFT -> options.leftKey;
            case RIGHT -> options.rightKey;
            default -> throw new IllegalArgumentException("Not a movement action: " + actionType);
        };
    }

    private boolean lookAtPlayer(MinecraftClient client, String targetPlayerName) {
        if (client == null || client.player == null || client.world == null || targetPlayerName == null || targetPlayerName.isBlank()) {
            return false;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, targetPlayerName);

        if (target == null) {
            return false;
        }

        rotateToward(player, target.getEyePos());
        return true;
    }

    private AbstractClientPlayerEntity findTargetPlayer(MinecraftClient client, ClientPlayerEntity player, String targetPlayerName) {
        for (AbstractClientPlayerEntity candidate : client.world.getPlayers()) {
            if (candidate == player || !candidate.isAlive() || candidate.isInvisibleTo(player)) {
                continue;
            }

            if (player.distanceTo(candidate) > LOOK_AT_RANGE) {
                continue;
            }

            if (candidate.getGameProfile().name().equalsIgnoreCase(targetPlayerName)) {
                return candidate;
            }
        }

        return null;
    }

    private void rotateToward(ClientPlayerEntity player, Vec3d targetPosition) {
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
}
