package com.foster.bambooclientbot.brain;

import com.foster.bambooclientbot.actions.ActionRequest;
import com.foster.bambooclientbot.commands.CommandHelp;
import com.foster.bambooclientbot.commands.CommandRequest;
import com.foster.bambooclientbot.intent.BotIntentType;
import com.foster.bambooclientbot.intent.IntentPlan;
import com.foster.bambooclientbot.intent.IntentRequest;
import com.foster.bambooclientbot.logging.BambooBotLog;
import com.foster.bambooclientbot.navigation.NavigationGrid;
import com.foster.bambooclientbot.navigation.PathPlanResult;
import com.foster.bambooclientbot.navigation.PathPlanner;
import com.foster.bambooclientbot.sensors.PlayerSensors;
import com.foster.bambooclientbot.sensors.WorldSensors;
import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.math.BlockPos;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BrainLoop {
    private static final int SNAPSHOT_DETAILS_MAX_LENGTH = 240;

    private final BotState state;
    private final PlayerSensors playerSensors;
    private final WorldSensors worldSensors;
    private final NavigationGrid navigationGrid = new NavigationGrid();
    private final PathPlanner pathPlanner = new PathPlanner(navigationGrid);
    private String lastArbitrationDecision = "";

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
            } else if ("nav check".equals(request.command())) {
                state.queueChatMessage(readNavigationCheck(client));
            } else if ("help".equals(request.command()) || "commands".equals(request.command())) {
                state.queueChatMessage(CommandHelp.format());
            } else if ("invalid_duration".equals(request.command())) {
                state.queueChatMessage("invalid duration");
            } else if ("invalid_transfer".equals(request.command())
                    || "invalid_item".equals(request.command())
                    || "invalid_count".equals(request.command())) {
                state.queueChatMessage("transfer failed");
            } else if ("invalid_count_item".equals(request.command())) {
                state.queueChatMessage("count failed");
            } else if ("invalid_have".equals(request.command())) {
                state.queueChatMessage("have failed");
            } else if ("invalid_drop".equals(request.command())) {
                state.queueChatMessage("drop failed");
            } else if ("invalid_coordinates".equals(request.command())) {
                state.queueChatMessage("goto failed");
            } else if ("invalid_path_coordinates".equals(request.command())) {
                state.queueChatMessage("path not found");
            } else if ("stop".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.STOP, ""));
            } else if ("autoswing on".equals(request.command()) || "swing on".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.SET_AUTOSWING, "on"));
            } else if ("autoswing off".equals(request.command()) || "swing off".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.SET_AUTOSWING, "off"));
            } else if ("autouse on".equals(request.command()) || "use on".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.SET_AUTOUSE, "on"));
            } else if ("autouse off".equals(request.command()) || "use off".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.SET_AUTOUSE, "off"));
            } else if ("autosneak on".equals(request.command()) || "sneak on".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.SET_AUTOSNEAK, "on"));
            } else if ("autosneak off".equals(request.command()) || "sneak off".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.SET_AUTOSNEAK, "off"));
            } else if ("farm off".equals(request.command()) || "actions off".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.DISABLE_FARM_ACTIONS, ""));
            } else if ("deposit".equals(request.command())) {
                state.queueAction(ActionRequest.itemTransfer(
                        ActionRequest.ActionType.DEPOSIT_ITEM,
                        request.itemId(),
                        request.requestedCount()
                ));
            } else if ("withdraw".equals(request.command())) {
                state.queueAction(ActionRequest.itemTransfer(
                        ActionRequest.ActionType.WITHDRAW_ITEM,
                        request.itemId(),
                        request.requestedCount()
                ));
            } else if ("count".equals(request.command())) {
                state.queueAction(ActionRequest.itemQuery(
                        ActionRequest.ActionType.COUNT_ITEM,
                        request.itemId(),
                        request.itemLabel()
                ));
            } else if ("have".equals(request.command())) {
                state.queueAction(ActionRequest.itemQuery(
                        ActionRequest.ActionType.HAVE_ITEM,
                        request.itemId(),
                        request.itemLabel(),
                        request.requestedCount()
                ));
            } else if ("drop".equals(request.command())) {
                state.queueAction(ActionRequest.targetedItem(
                        ActionRequest.ActionType.DROP_ITEM,
                        request.targetPlayer(),
                        request.itemId(),
                        request.itemLabel(),
                        request.requestedCount()
                ));
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
            } else if ("goto".equals(request.command())) {
                state.queueAction(ActionRequest.gotoCoordinates(request.gotoTarget()));
            } else if ("path".equals(request.command())) {
                state.queueChatMessage(readPathPlan(client, request));
            } else if ("interact".equals(request.command()) || "use".equals(request.command()) || "open".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.INTERACT_TARGETED_BLOCK, state.lookedAtBlock()));
            } else if ("close".equals(request.command()) || "close screen".equals(request.command()) || "close menu".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.CLOSE_CURRENT_SCREEN, ""));
            } else if ("snapshot details".equals(request.command()) || "container details".equals(request.command())) {
                queueContainerSnapshotDetails();
            } else if ("inspect container".equals(request.command()) || "container".equals(request.command()) || "snapshot".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.CAPTURE_CONTAINER_SNAPSHOT, ""));
            } else if ("inventory details".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.CAPTURE_INVENTORY_DETAILS, ""));
            } else if ("inventory".equals(request.command()) || "inventory snapshot".equals(request.command())) {
                state.queueAction(new ActionRequest(ActionRequest.ActionType.CAPTURE_INVENTORY_SNAPSHOT, ""));
            }
        }
    }

    public IntentPlan arbitrateIntents(MinecraftClient client, List<IntentRequest> requests) {
        Set<BotIntentType> approved = EnumSet.noneOf(BotIntentType.class);
        Set<BotIntentType> requested = EnumSet.noneOf(BotIntentType.class);
        StringBuilder rejected = new StringBuilder();

        for (IntentRequest request : requests) {
            requested.add(request.type());

            if (request.type() == BotIntentType.PASSIVE_AWARENESS) {
                approved.add(request.type());
            } else if (request.type() == BotIntentType.AUTO_EAT) {
                String rejectionReason = autoEatRejectionReason(client);

                if (rejectionReason.isBlank()) {
                    approved.add(request.type());
                } else {
                    appendRejected(rejected, request.source(), request.type(), rejectionReason);
                }
            }
        }

        logArbitration(requested, approved, rejected);
        return new IntentPlan(approved);
    }

    private String autoEatRejectionReason(MinecraftClient client) {
        ActionRequest pendingAction = state.peekAction();

        if (pendingAction != null) {
            return "pending_action=" + pendingAction.actionType().name();
        }

        if (isContainerOpen(client)) {
            return "container_open";
        }

        return "";
    }

    private boolean isContainerOpen(MinecraftClient client) {
        return client != null
                && client.currentScreen instanceof HandledScreen<?>
                && !(client.currentScreen instanceof InventoryScreen);
    }

    private void appendRejected(StringBuilder rejected, String source, BotIntentType type, String reason) {
        if (rejected.length() > 0) {
            rejected.append(',');
        }

        rejected.append(source).append(':').append(type).append(':').append(reason);
    }

    private void logArbitration(Set<BotIntentType> requested, Set<BotIntentType> approved, StringBuilder rejected) {
        String decision = "ARBITRATION requested=" + requested
                + " approved=" + approved
                + " rejected=" + (rejected.length() == 0 ? "none" : rejected);

        if (decision.equals(lastArbitrationDecision)) {
            return;
        }

        lastArbitrationDecision = decision;
        BambooBotLog.info(decision);
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
        return "slot" + (entry.slot() + 1) + ":" + compactItemId(entry.itemId()) + "x" + entry.count();
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

    private String readNavigationCheck(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return "nav unavailable";
        }

        BlockPos current = client.player.getBlockPos();
        return "nav current=" + navigationGrid.evaluate(client.world, current).label()
                + " north=" + navigationGrid.evaluate(client.world, current.north()).label()
                + " east=" + navigationGrid.evaluate(client.world, current.east()).label()
                + " south=" + navigationGrid.evaluate(client.world, current.south()).label()
                + " west=" + navigationGrid.evaluate(client.world, current.west()).label();
    }

    private String readPathPlan(MinecraftClient client, CommandRequest request) {
        if (client == null || client.player == null || client.world == null || request.gotoTarget() == null) {
            return "path not found";
        }

        BlockPos start = client.player.getBlockPos();
        BlockPos target = BlockPos.ofFloored(request.gotoTarget().x(), request.gotoTarget().y(), request.gotoTarget().z());
        PathPlanResult result = pathPlanner.plan(client.world, start, target);

        if (!result.found()) {
            return "path not found";
        }

        return "path found nodes=" + result.nodes()
                + " length=" + String.format(Locale.ROOT, "%.1f", result.length());
    }
}
