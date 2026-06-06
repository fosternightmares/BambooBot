package com.foster.bambooclientbot.actions;

import com.foster.bambooclientbot.commands.ItemResolver;
import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.DropResult;
import com.foster.bambooclientbot.state.InventorySnapshot;
import com.foster.bambooclientbot.state.TransferResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

class InventoryActionExecutor {
    private static final double LOOK_AT_RANGE = 32.0;
    private static final int MAIN_INVENTORY_SLOT_COUNT = 36;
    private static final int TOTAL_INVENTORY_SLOT_COUNT = 41;
    private static final int INVENTORY_DETAILS_MAX_LENGTH = 240;

    private final BotState state;
    private final LookController lookController;

    InventoryActionExecutor(BotState state, LookController lookController) {
        this.state = state;
        this.lookController = lookController;
    }

    void captureInventorySnapshot(MinecraftClient client, ActionRequest request, boolean includeDetails) {
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

    InventorySnapshot readInventorySnapshot(MinecraftClient client) {
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

    void countItem(MinecraftClient client, ActionRequest request) {
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

    void haveItem(MinecraftClient client, ActionRequest request) {
        String itemId = normalizeItemId(request.itemId());

        if (itemId.isBlank() || request.requestedCount() <= 0) {
            request.setStatus(ActionRequest.ActionStatus.FAILED);
            state.setLastActionResult(request.actionType().name(), "failed", "invalid_item");
            state.queueChatMessage("have failed");
            return;
        }

        InventorySnapshot snapshot = snapshotForItemQuery(client, request, "have failed");

        if (snapshot == null) {
            return;
        }

        int count = countSnapshotItem(snapshot, itemId);
        boolean hasEnough = count >= request.requestedCount();
        request.setStatus(ActionRequest.ActionStatus.COMPLETE);
        state.setLastActionResult(request.actionType().name(), hasEnough ? "yes" : "no", "");
        state.queueChatMessage("have " + countItemLabel(request, itemId)
                + " " + count + "/" + request.requestedCount()
                + " " + (hasEnough ? "yes" : "no"));
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

    void dropItem(MinecraftClient client, ActionRequest request) {
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

        lookController.rotateToward(player, target.getEyePos());
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

    void transferItems(MinecraftClient client, ActionRequest request, String operation, boolean deposit) {
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

    List<Slot> containerSlots(List<Slot> slots) {
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

    private String normalizeItemId(String itemId) {
        return ItemResolver.resolveItemArg(itemId);
    }

    private String compactItemId(String itemId) {
        if (itemId.startsWith("minecraft:")) {
            return itemId.substring("minecraft:".length());
        }

        return itemId;
    }
}
