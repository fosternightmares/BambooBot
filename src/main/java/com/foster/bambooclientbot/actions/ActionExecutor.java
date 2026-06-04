package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.commands.ItemResolver;
import com.foster.bambooclientbot.navigation.NavigationGrid;
import com.foster.bambooclientbot.navigation.PathPlanResult;
import com.foster.bambooclientbot.navigation.PathPlanner;
import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerSnapshot;
import com.foster.bambooclientbot.state.DropResult;
import com.foster.bambooclientbot.state.GotoResult;
import com.foster.bambooclientbot.state.GotoTarget;
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
    private static final double GOTO_HORIZONTAL_ARRIVAL_DISTANCE = 1.5;
    private static final double GOTO_VERTICAL_ARRIVAL_DISTANCE = 2.0;
    private static final double GOTO_WAYPOINT_DISTANCE = 0.8;
    private static final double GOTO_JUMP_TARGET_Y_DELTA = 0.5;
    private static final double FOLLOW_JUMP_TARGET_Y_DELTA = 0.5;
    private static final int FOLLOW_JUMP_COOLDOWN_TICKS = 8;
    private static final long AUTOSWING_INTERVAL_MILLIS = 1_000L;
    private static final int MAIN_INVENTORY_SLOT_COUNT = 36;
    private static final int TOTAL_INVENTORY_SLOT_COUNT = 41;
    private static final int INVENTORY_DETAILS_MAX_LENGTH = 240;

    private final BotState state;
    private final PathPlanner pathPlanner = new PathPlanner(new NavigationGrid());
    private ActionRequest timedMovement;
    private int timedMovementTicksRemaining;
    private ActionRequest activeApproach;
    private ActionRequest activeFollow;
    private ActionRequest activeGoto;
    private List<BlockPos> activeGotoRoute = List.of();
    private int activeGotoRouteIndex;
    private int followJumpCooldownTicks;

    public ActionExecutor(BotState state) {
        this.state = state;
    }

    public void tick(MinecraftClient client) {
        ActionRequest request = state.pollAction();

        if (request != null) {
            executeAction(client, request);
            tickAutoSneak(client);
            tickAutoUse(client);
            tickAutoSwing(client);
            return;
        }

        tickApproach(client);
        tickFollow(client);
        tickGoto(client);
        tickTimedMovement(client);
        tickAutoSneak(client);
        tickAutoUse(client);
        tickAutoSwing(client);
    }

    private void executeAction(MinecraftClient client, ActionRequest request) {
        request.setStatus(ActionRequest.ActionStatus.RUNNING);
        state.setActiveAction(request.actionType().name());

        try {
            if (request.actionType() == ActionRequest.ActionType.STOP) {
                activeApproach = null;
                activeFollow = null;
                cancelActiveGoto("goto_cancelled");
                timedMovement = null;
                timedMovementTicksRemaining = 0;
                followJumpCooldownTicks = 0;
                state.setAutoSwing(false);
                state.setLastSwingTimeMillis(0L);
                state.setAutoUse(false);
                state.setAutoSneak(false);
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
            } else if (request.actionType() == ActionRequest.ActionType.GOTO_COORDINATES) {
                startGoto(client, request);
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
            } else if (request.actionType() == ActionRequest.ActionType.COUNT_ITEM) {
                countItem(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.HAVE_ITEM) {
                haveItem(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.DROP_ITEM) {
                dropItem(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.SET_AUTOSWING) {
                setAutoSwing(request);
            } else if (request.actionType() == ActionRequest.ActionType.SET_AUTOUSE) {
                setAutoUse(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.SET_AUTOSNEAK) {
                setAutoSneak(client, request);
            } else if (request.actionType() == ActionRequest.ActionType.DISABLE_FARM_ACTIONS) {
                disableFarmActions(client, request);
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

    private void setAutoSwing(ActionRequest request) {
        boolean enabled = "on".equals(request.actionData());
        state.setAutoSwing(enabled);
        state.setLastSwingTimeMillis(0L);
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), enabled ? "enabled" : "disabled", "");
        state.queueChatMessage(enabled ? "autoswing enabled" : "autoswing disabled");
    }

    private void setAutoUse(MinecraftClient client, ActionRequest request) {
        boolean enabled = "on".equals(request.actionData());
        state.setAutoUse(enabled);

        if (client != null && client.options != null) {
            client.options.useKey.setPressed(enabled);
        }

        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), enabled ? "enabled" : "disabled", "");
        state.queueChatMessage(enabled ? "autouse enabled" : "autouse disabled");
    }

    private void setAutoSneak(MinecraftClient client, ActionRequest request) {
        boolean enabled = "on".equals(request.actionData());
        state.setAutoSneak(enabled);

        if (client != null && client.options != null) {
            client.options.sneakKey.setPressed(enabled);
        }

        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), enabled ? "enabled" : "disabled", "");
        state.queueChatMessage(enabled ? "autosneak enabled" : "autosneak disabled");
    }

    private void disableFarmActions(MinecraftClient client, ActionRequest request) {
        state.setAutoSwing(false);
        state.setLastSwingTimeMillis(0L);
        state.setAutoUse(false);
        state.setAutoSneak(false);

        if (client != null && client.options != null) {
            client.options.useKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
        }

        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage("farm actions disabled");
    }

    private void captureInventorySnapshot(MinecraftClient client, ActionRequest request, boolean includeDetails) {
        InventorySnapshot snapshot = readInventorySnapshot(client);

        if (snapshot == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "inventory_unavailable");
            state.queueChatMessage("inventory_unavailable");
            return;
        }

        state.setInventorySnapshot(snapshot);
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");

        if (includeDetails) {
            state.queueChatMessage(formatInventoryDetails(snapshot));
        } else {
            state.queueChatMessage("inventory snapshot captured");
        }
    }

    private InventorySnapshot readInventorySnapshot(MinecraftClient client) {
        if (client == null || client.player == null) {
            return null;
        }

        PlayerInventory inventory = client.player.getInventory();
        List<InventorySnapshot.Entry> entries = new ArrayList<>();
        int slotCount = Math.min(TOTAL_INVENTORY_SLOT_COUNT, inventory.size());

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

        return new InventorySnapshot(System.currentTimeMillis(), slotCount, List.copyOf(entries));
    }

    private void countItem(MinecraftClient client, ActionRequest request) {
        InventorySnapshot snapshot = snapshotForItemQuery(client, request, "count failed");

        if (snapshot == null) {
            return;
        }

        String itemId = normalizeItemId(request.itemId());
        int count = countSnapshotItem(snapshot, itemId);
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage(countItemLabel(request, itemId) + " x" + count);
    }

    private void haveItem(MinecraftClient client, ActionRequest request) {
        if (request.requestedCount() <= 0) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "invalid_count");
            state.queueChatMessage("have failed");
            return;
        }

        InventorySnapshot snapshot = snapshotForItemQuery(client, request, "have failed");

        if (snapshot == null) {
            return;
        }

        String itemId = normalizeItemId(request.itemId());
        int count = countSnapshotItem(snapshot, itemId);
        String result = count >= request.requestedCount() ? "yes" : "no";
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), "success", "");
        state.queueChatMessage("have " + countItemLabel(request, itemId)
                + " " + result
                + " count=" + count
                + " requested=" + request.requestedCount());
    }

    private InventorySnapshot snapshotForItemQuery(MinecraftClient client, ActionRequest request, String failureMessage) {
        String itemId = normalizeItemId(request.itemId());

        if (itemId.isBlank()) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "invalid_item");
            state.queueChatMessage(failureMessage);
            return null;
        }

        InventorySnapshot snapshot = state.inventorySnapshot();

        if (snapshot == null) {
            snapshot = readInventorySnapshot(client);

            if (snapshot == null) {
                request.setStatus(ActionRequest.ActionStatus.FAILED);
                state.setLastActionResult(request.actionType().name(), "failed", "inventory_unavailable");
                state.queueChatMessage(failureMessage);
                return null;
            }

            state.setInventorySnapshot(snapshot);
        }

        return snapshot;
    }

    private int countSnapshotItem(InventorySnapshot snapshot, String itemId) {
        int count = 0;

        for (InventorySnapshot.Entry entry : snapshot.entries()) {
            if (entry.itemId().equals(itemId)) {
                count += entry.count();
            }
        }

        return count;
    }

    private String countItemLabel(ActionRequest request, String itemId) {
        if (request.itemLabel() != null && !request.itemLabel().isBlank()) {
            return request.itemLabel();
        }

        return compactItemId(itemId);
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
        return inventorySlotLabel(entry.slot()) + ":" + compactItemId(entry.itemId()) + "x" + entry.count();
    }

    private String inventorySlotLabel(int slot) {
        if (slot >= 0 && slot < 9) {
            return "hotbar" + (slot + 1);
        }

        if (slot >= 9 && slot < MAIN_INVENTORY_SLOT_COUNT) {
            return "inv" + (slot - 8);
        }

        return switch (slot) {
            case 36 -> "boots";
            case 37 -> "leggings";
            case 38 -> "chestplate";
            case 39 -> "helmet";
            case 40 -> "offhand";
            default -> "slot" + slot;
        };
    }

    private void dropItem(MinecraftClient client, ActionRequest request) {
        String itemId = normalizeItemId(request.itemId());

        if (itemId.isBlank()) {
            failDrop(request, itemId, 0, "invalid_item");
            return;
        }

        if (request.requestedCount() <= 0) {
            failDrop(request, itemId, 0, "invalid_count");
            return;
        }

        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            failDrop(request, itemId, 0, "target_player_not_found");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, request.actionData());

        if (target == null) {
            failDrop(request, itemId, 0, "target_player_not_found");
            return;
        }

        ScreenHandler handler = player.playerScreenHandler;

        if (handler == null) {
            failDrop(request, itemId, 0, "drop_failed");
            return;
        }

        List<Slot> normalSlots = normalInventorySlots(handler.slots);
        List<Slot> equippedSlots = equippedInventorySlots(handler.slots);
        int normalAvailable = countMatchingItems(normalSlots, itemId);
        List<Slot> dropSlots = normalAvailable > 0 ? normalSlots : equippedSlots;
        int available = normalAvailable > 0 ? normalAvailable : countMatchingItems(equippedSlots, itemId);

        if (available <= 0) {
            failDrop(request, itemId, 0, "item_not_found");
            return;
        }

        rotateToward(player, target.getEyePos());
        int toDrop = Math.min(request.requestedCount(), available);
        int droppedCount = dropExactCount(client, handler, dropSlots, itemId, toDrop);

        if (droppedCount <= 0) {
            failDrop(request, itemId, 0, normalAvailable > 0 ? "drop_failed" : "unequip_failed");
            return;
        }

        String result = droppedCount == request.requestedCount() ? "success" : "partial";
        String reason = droppedCount == request.requestedCount() ? "" : "insufficient_quantity";
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), result, reason);
        state.setLastDropResult(new DropResult(
                itemId,
                request.actionData(),
                request.requestedCount(),
                droppedCount,
                result,
                reason
        ));
        state.setInventorySnapshot(readInventorySnapshot(client));
        state.queueChatMessage("dropped " + droppedCount + " " + countItemLabel(request, itemId));
    }

    private List<Slot> normalInventorySlots(List<Slot> slots) {
        List<Slot> matchingSlots = new ArrayList<>();

        for (Slot slot : slots) {
            if (isPlayerInventorySlot(slot) && slot.id >= 9 && slot.id <= 44) {
                matchingSlots.add(slot);
            }
        }

        return matchingSlots;
    }

    private List<Slot> equippedInventorySlots(List<Slot> slots) {
        List<Slot> matchingSlots = new ArrayList<>();

        for (Slot slot : slots) {
            if (isPlayerInventorySlot(slot) && ((slot.id >= 5 && slot.id <= 8) || slot.id == 45)) {
                matchingSlots.add(slot);
            }
        }

        return matchingSlots;
    }

    private int dropExactCount(MinecraftClient client, ScreenHandler handler, List<Slot> inventorySlots,
                               String itemId, int requestedCount) {
        int droppedCount = 0;

        for (Slot slot : inventorySlots) {
            if (droppedCount >= requestedCount) {
                break;
            }

            ItemStack stack = slot.getStack();

            if (stack.isEmpty() || !matchesItem(stack, itemId)) {
                continue;
            }

            int toDropFromSlot = Math.min(stack.getCount(), requestedCount - droppedCount);

            for (int i = 0; i < toDropFromSlot; i++) {
                client.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.THROW, client.player);
                droppedCount++;
            }
        }

        return droppedCount;
    }

    private void failDrop(ActionRequest request, String itemId, int droppedCount, String reason) {
        request.setStatus(ActionRequest.ActionStatus.FAILED);
        state.setLastActionResult(request.actionType().name(), "failed", reason);
        state.setLastDropResult(new DropResult(
                itemId,
                request.actionData(),
                request.requestedCount(),
                droppedCount,
                "failed",
                reason
        ));
        state.queueChatMessage("drop failed");
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
            state.setFollowJump(false);
            state.queueChatMessage("action failed");
            return;
        }

        ClientPlayerEntity player = client.player;
        AbstractClientPlayerEntity target = findTargetPlayer(client, player, activeFollow.actionData());

        if (target == null) {
            activeFollow.setStatus(ActionRequest.ActionStatus.FAILED);
            activeFollow = null;
            stop(client);
            state.setFollowJump(false);
            state.queueChatMessage("player not found");
            return;
        }

        rotateToward(player, target.getEyePos());
        tickFollowJumpCooldown();
        boolean movingForward = false;

        if (player.distanceTo(target) > FOLLOW_DISTANCE) {
            clearDirectionalKeys(client.options);
            client.options.forwardKey.setPressed(true);
            movingForward = true;
        } else {
            clearDirectionalKeys(client.options);
            client.options.jumpKey.setPressed(false);
            state.setFollowJump(false);
        }

        updateFollowJump(client.options, player, target, movingForward);
        updateFollowSprint(client.options, player.distanceTo(target));
    }

    private void tickGoto(MinecraftClient client) {
        if (activeGoto == null) {
            return;
        }

        GotoTarget target = activeGoto.gotoTarget();

        if (client == null || client.player == null || client.options == null || target == null || activeGotoRoute.isEmpty()) {
            failGoto(client, "movement_unavailable");
            return;
        }

        ClientPlayerEntity player = client.player;
        double horizontalDistance = horizontalDistance(player, target);
        double verticalDifference = Math.abs(player.getY() - target.y());
        state.setActiveGoto(target, horizontalDistance, activeGotoRouteIndex, activeGotoRoute.size());

        if (horizontalDistance <= GOTO_HORIZONTAL_ARRIVAL_DISTANCE
                && verticalDifference <= GOTO_VERTICAL_ARRIVAL_DISTANCE) {
            stop(client);
            activeGoto.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.setLastGotoResult(new GotoResult(target, "success", ""));
            state.clearActiveGoto();
            activeGoto = null;
            activeGotoRoute = List.of();
            activeGotoRouteIndex = 0;
            return;
        }

        BlockPos waypoint = currentGotoWaypoint(player);
        Vec3d waypointCenter = waypointCenter(waypoint);
        rotateToward(player, waypointCenter);
        tickFollowJumpCooldown();
        clearDirectionalKeys(client.options);
        client.options.forwardKey.setPressed(true);
        updateGotoJump(client.options, player, waypoint);
        updateFollowSprint(client.options, horizontalDistance);
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
        cancelActiveGoto("goto_cancelled");
        followJumpCooldownTicks = 0;
        GameOptions options = client.options;
        clearDirectionalKeys(options);
        options.jumpKey.setPressed(false);
        state.setFollowJump(false);
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
        cancelActiveGoto("goto_cancelled");
        followJumpCooldownTicks = 0;
        clearDirectionalKeys(client.options);
        client.options.jumpKey.setPressed(false);
        state.setFollowJump(false);
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
        cancelActiveGoto("goto_cancelled");
        followJumpCooldownTicks = 0;
        clearDirectionalKeys(client.options);
        client.options.sprintKey.setPressed(false);
        rotateToward(player, target.getEyePos());

        if (player.distanceTo(target) > FOLLOW_DISTANCE) {
            client.options.forwardKey.setPressed(true);
        }

        updateFollowSprint(client.options, player.distanceTo(target));
        state.setFollowJump(false);
        activeFollow = request;
        state.queueChatMessage("following " + request.actionData());
    }

    private void startGoto(MinecraftClient client, ActionRequest request) {
        GotoTarget target = request.gotoTarget();

        if (client == null || client.player == null || client.world == null || client.options == null || target == null) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastGotoResult(new GotoResult(target, "failed", "movement_unavailable"));
            state.clearActiveGoto();
            state.queueChatMessage("goto failed");
            return;
        }

        cancelActiveGoto("goto_cancelled");
        BlockPos start = client.player.getBlockPos();
        BlockPos targetPosition = BlockPos.ofFloored(target.x(), target.y(), target.z());
        PathPlanResult plan = pathPlanner.plan(client.world, start, targetPosition);

        if (!plan.found()) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastGotoResult(new GotoResult(target, "failed", plan.reason()));
            state.clearActiveGoto();
            state.queueChatMessage("goto failed");
            return;
        }

        timedMovement = null;
        timedMovementTicksRemaining = 0;
        activeApproach = null;
        activeFollow = null;
        followJumpCooldownTicks = 0;

        double horizontalDistance = horizontalDistance(client.player, target);
        double verticalDifference = Math.abs(client.player.getY() - target.y());

        if (horizontalDistance <= GOTO_HORIZONTAL_ARRIVAL_DISTANCE
                && verticalDifference <= GOTO_VERTICAL_ARRIVAL_DISTANCE) {
            stop(client);
            request.setStatus(ActionRequest.ActionStatus.COMPLETE);
            state.setLastGotoResult(new GotoResult(target, "success", ""));
            state.clearActiveGoto();
            state.queueChatMessage("arrived");
            return;
        }

        clearDirectionalKeys(client.options);
        client.options.jumpKey.setPressed(false);
        state.setFollowJump(false);
        activeGotoRoute = plan.path();
        activeGotoRouteIndex = activeGotoRoute.size() > 1 ? 1 : 0;
        rotateToward(client.player, waypointCenter(activeGotoRoute.get(activeGotoRouteIndex)));
        state.setActiveGoto(target, horizontalDistance, activeGotoRouteIndex, activeGotoRoute.size());
        activeGoto = request;
        state.queueChatMessage("going to " + target.formatForChat());
    }

    private void failGoto(MinecraftClient client, String reason) {
        GotoTarget target = activeGoto == null ? null : activeGoto.gotoTarget();

        if (activeGoto != null) {
            activeGoto.setStatus(ActionRequest.ActionStatus.FAILED);
        }

        stop(client);
        state.setLastGotoResult(new GotoResult(target, "failed", reason));
        state.clearActiveGoto();
        activeGoto = null;
        activeGotoRoute = List.of();
        activeGotoRouteIndex = 0;
    }

    private void cancelActiveGoto(String reason) {
        if (activeGoto == null) {
            state.clearActiveGoto();
            return;
        }

        activeGoto.setStatus(ActionRequest.ActionStatus.FAILED);
        state.setLastGotoResult(new GotoResult(activeGoto.gotoTarget(), "failed", reason));
        activeGoto = null;
        activeGotoRoute = List.of();
        activeGotoRouteIndex = 0;
        state.clearActiveGoto();
    }

    private void stop(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        GameOptions options = client.options;
        clearDirectionalKeys(options);
        options.jumpKey.setPressed(false);
        followJumpCooldownTicks = 0;
        state.setFollowJump(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }

    private void tickAutoUse(MinecraftClient client) {
        if (!state.autoUse()) {
            return;
        }

        if (client == null || client.options == null) {
            state.setAutoUse(false);
            return;
        }

        client.options.useKey.setPressed(true);
    }

    private void tickAutoSneak(MinecraftClient client) {
        if (!state.autoSneak()) {
            return;
        }

        if (client == null || client.options == null) {
            state.setAutoSneak(false);
            return;
        }

        client.options.sneakKey.setPressed(true);
    }

    private void tickAutoSwing(MinecraftClient client) {
        if (!state.autoSwing()) {
            return;
        }

        if (client == null || client.player == null) {
            state.setAutoSwing(false);
            state.setLastSwingTimeMillis(0L);
            return;
        }

        long now = System.currentTimeMillis();

        if (now - state.lastSwingTimeMillis() < AUTOSWING_INTERVAL_MILLIS) {
            return;
        }

        client.player.swingHand(Hand.MAIN_HAND);
        state.setLastSwingTimeMillis(now);
    }

    private void updateFollowSprint(GameOptions options, double targetDistance) {
        if (targetDistance > FOLLOW_SPRINT_ENABLE_DISTANCE) {
            options.sprintKey.setPressed(true);
        } else if (targetDistance <= FOLLOW_SPRINT_DISABLE_DISTANCE) {
            options.sprintKey.setPressed(false);
        }
    }

    private void tickFollowJumpCooldown() {
        if (followJumpCooldownTicks > 0) {
            followJumpCooldownTicks--;
        }
    }

    private void updateFollowJump(GameOptions options, ClientPlayerEntity player, AbstractClientPlayerEntity target, boolean movingForward) {
        if (!movingForward || !canFollowJump(player)) {
            options.jumpKey.setPressed(false);
            state.setFollowJump(false);
            return;
        }

        boolean targetAbove = target.getY() - player.getY() >= FOLLOW_JUMP_TARGET_Y_DELTA
                && player.distanceTo(target) <= FOLLOW_DISTANCE + 2.0;
        boolean blocked = player.horizontalCollision;

        if ((targetAbove || blocked) && followJumpCooldownTicks <= 0) {
            options.jumpKey.setPressed(true);
            state.setFollowJump(true);
            followJumpCooldownTicks = FOLLOW_JUMP_COOLDOWN_TICKS;
            return;
        }

        options.jumpKey.setPressed(false);
        state.setFollowJump(false);
    }

    private BlockPos currentGotoWaypoint(ClientPlayerEntity player) {
        while (activeGotoRouteIndex < activeGotoRoute.size() - 1
                && horizontalDistance(player, activeGotoRoute.get(activeGotoRouteIndex)) <= GOTO_WAYPOINT_DISTANCE) {
            activeGotoRouteIndex++;
        }

        return activeGotoRoute.get(activeGotoRouteIndex);
    }

    private Vec3d waypointCenter(BlockPos waypoint) {
        return new Vec3d(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5);
    }

    private void updateGotoJump(GameOptions options, ClientPlayerEntity player, BlockPos waypoint) {
        if (!canFollowJump(player)) {
            options.jumpKey.setPressed(false);
            state.setFollowJump(false);
            return;
        }

        boolean targetAbove = waypoint.getY() - player.getY() >= GOTO_JUMP_TARGET_Y_DELTA
                && horizontalDistance(player, waypoint) <= FOLLOW_DISTANCE + 2.0;
        boolean blocked = player.horizontalCollision;

        if ((targetAbove || blocked) && followJumpCooldownTicks <= 0) {
            options.jumpKey.setPressed(true);
            state.setFollowJump(true);
            followJumpCooldownTicks = FOLLOW_JUMP_COOLDOWN_TICKS;
            return;
        }

        options.jumpKey.setPressed(false);
        state.setFollowJump(false);
    }

    private double horizontalDistance(ClientPlayerEntity player, GotoTarget target) {
        double deltaX = target.x() - player.getX();
        double deltaZ = target.z() - player.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private double horizontalDistance(ClientPlayerEntity player, BlockPos target) {
        double deltaX = target.getX() + 0.5 - player.getX();
        double deltaZ = target.getZ() + 0.5 - player.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private boolean canFollowJump(ClientPlayerEntity player) {
        return player.isOnGround()
                && !player.isTouchingWater()
                && !player.isSwimming()
                && !player.isClimbing();
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
