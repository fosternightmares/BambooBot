package com.foster.bambooclientbot.brain;

import com.foster.bambooclientbot.actions.ActionRequest;
import com.foster.bambooclientbot.commands.CommandRequest;
import com.foster.bambooclientbot.sensors.PlayerSensors;
import com.foster.bambooclientbot.sensors.WorldSensors;
import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerSnapshot;
import net.minecraft.client.MinecraftClient;

public class BrainLoop {
    private static final int SNAPSHOT_DETAILS_MAX_LENGTH = 240;

    private final BotState state;
    private final PlayerSensors playerSensors;
    private final WorldSensors worldSensors;

    public BrainLoop(BotState state, PlayerSensors playerSensors, WorldSensors worldSensors) {
        this.state = state;
        this.playerSensors = playerSensors;
        this.worldSensors = worldSensors;
    }

    public void tick(MinecraftClient client) {
        CommandRequest request;

        while ((request = state.pollCommand()) != null) {
            if ("status".equals(request.command())) {
                state.queueChatMessage(playerSensors.readStatus(client, state));
            } else if ("nearby".equals(request.command())) {
                state.queueChatMessage(worldSensors.readNearby(client));
            } else if ("invalid_duration".equals(request.command())) {
                state.queueChatMessage("invalid duration");
            } else if ("stop".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.STOP, ""));
            } else if ("forward".equals(request.command())) {
                queueMovement(ActionRequest.ActionType.FORWARD, request);
            } else if ("back".equals(request.command())) {
                queueMovement(ActionRequest.ActionType.BACK, request);
            } else if ("left".equals(request.command())) {
                queueMovement(ActionRequest.ActionType.LEFT, request);
            } else if ("right".equals(request.command())) {
                queueMovement(ActionRequest.ActionType.RIGHT, request);
            } else if ("look_at".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.LOOK_AT_PLAYER, request.targetPlayer()));
            } else if ("approach".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.APPROACH_PLAYER, request.targetPlayer()));
            } else if ("follow".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.FOLLOW_PLAYER, request.targetPlayer()));
            } else if ("interact".equals(request.command()) || "use".equals(request.command()) || "open".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.INTERACT_TARGETED_BLOCK, state.lookedAtBlock()));
            } else if ("close".equals(request.command()) || "close screen".equals(request.command()) || "close menu".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.CLOSE_CURRENT_SCREEN, ""));
            } else if ("snapshot details".equals(request.command()) || "container details".equals(request.command())) {
                queueContainerSnapshotDetails();
            } else if ("inspect container".equals(request.command()) || "container".equals(request.command()) || "snapshot".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.CAPTURE_CONTAINER_SNAPSHOT, ""));
            }
        }
    }

    private void queueContainerSnapshotDetails() {
        ContainerSnapshot snapshot = state.containerSnapshot();

        if (snapshot == null) {
            state.queueChatMessage("snapshot_missing");
            return;
        }

        state.queueChatMessage(formatContainerSnapshot(snapshot));
    }

    private String formatContainerSnapshot(ContainerSnapshot snapshot) {
        String prefix = "snapshot slots=" + snapshot.slotCount() + " occupied=" + snapshot.occupiedSlots() + " [";
        StringBuilder message = new StringBuilder(prefix);
        int includedEntries = 0;

        for (ContainerSnapshot.Entry entry : snapshot.entries()) {
            String formattedEntry = formatSnapshotEntry(entry);
            int separatorLength = includedEntries == 0 ? 0 : 1;
            int remainingAfterEntry = snapshot.occupiedSlots() - includedEntries - 1;
            String truncationSuffix = remainingAfterEntry > 0 ? ",...+" + remainingAfterEntry : "";
            int suffixLength = truncationSuffix.length() + 1;

            if (message.length() + separatorLength + formattedEntry.length() + suffixLength > SNAPSHOT_DETAILS_MAX_LENGTH) {
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

    private String formatSnapshotEntry(ContainerSnapshot.Entry entry) {
        return entry.slot() + ":" + compactItemId(entry.itemId()) + "x" + entry.count();
    }

    private String compactItemId(String itemId) {
        if (itemId.startsWith("minecraft:")) {
            return itemId.substring("minecraft:".length());
        }

        return itemId;
    }

    private void queueMovement(ActionRequest.ActionType actionType, CommandRequest request) {
        state.queueAction(new ActionRequest(actionType, "", request.durationTicks(), request.durationSeconds()));
    }
}
