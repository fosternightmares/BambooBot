package com.foster.bambooclientbot.sensors;

import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerSnapshot;
import com.foster.bambooclientbot.state.InventorySnapshot;
import com.foster.bambooclientbot.state.LookedAtBlock;
import com.foster.bambooclientbot.state.ThreatInfo;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlayerSensors {
    private static final int MAX_THREAT_GROUPS = 6;

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
                + " currentAction=" + state.activeAction()
                + " threatCount=" + state.threatCount();
    }

    public String readMovementStatus(MinecraftClient client, BotState state) {
        if (client == null || client.options == null) {
            return "movement unavailable";
        }

        GameOptions options = client.options;
        return "forward=" + options.forwardKey.isPressed()
                + " back=" + options.backKey.isPressed()
                + " left=" + options.leftKey.isPressed()
                + " right=" + options.rightKey.isPressed()
                + " sprint=" + options.sprintKey.isPressed()
                + " sneak=" + options.sneakKey.isPressed()
                + " jump=" + options.jumpKey.isPressed()
                + " autoSwing=" + state.autoSwing()
                + " autoUse=" + state.autoUse()
                + " autoSneak=" + state.autoSneak();
    }

    public String readRouteStatus(BotState state) {
        return state.routeStatus()
                + " " + state.gotoStatus()
                + " " + state.recentRouteStatus();
    }

    public String readFollowStatus(BotState state) {
        return state.followRouteStatus()
                + " followMode=route"
                + " followRoute=" + state.routeStatus()
                + " followJump=" + state.followJump()
                + " directChase=unavailable"
                + " predictedTarget=unavailable";
    }

    public String readInventoryStatus(BotState state) {
        return inventorySnapshotStatus(state)
                + " " + state.lastTransferStatus()
                + " " + state.lastDropStatus();
    }

    public String readContainerStatus(BotState state) {
        return state.containerState().format()
                + " " + containerSnapshotStatus(state);
    }

    public String readThreatStatus(BotState state) {
        List<ThreatInfo> threats = state.threats();

        if (threats.isEmpty()) {
            return "threatCount=0 underThreat=false nearest=none groups=none";
        }

        ThreatInfo nearest = threats.stream()
                .min(Comparator.comparingDouble(ThreatInfo::distance))
                .orElse(threats.get(0));

        return "threatCount=" + threats.size()
                + " underThreat=" + state.underThreat()
                + " nearest=" + nearest.name() + ":" + formatDistance(nearest.distance())
                + targetingSummary(threats)
                + " groups=" + threatGroups(threats);
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

    private String threatGroups(List<ThreatInfo> threats) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (ThreatInfo threat : threats) {
            counts.put(threat.name(), counts.getOrDefault(threat.name(), 0) + 1);
        }

        String groups = counts.entrySet().stream()
                .limit(MAX_THREAT_GROUPS)
                .map(entry -> entry.getKey() + "x" + entry.getValue())
                .collect(Collectors.joining(","));

        if (counts.size() > MAX_THREAT_GROUPS) {
            groups += ",...+" + (counts.size() - MAX_THREAT_GROUPS);
        }

        return groups;
    }

    private String targetingSummary(List<ThreatInfo> threats) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (ThreatInfo threat : threats) {
            if (threat.targetingBot()) {
                counts.put(threat.name(), counts.getOrDefault(threat.name(), 0) + 1);
            }
        }

        if (counts.isEmpty()) {
            return "";
        }

        String targeting = counts.entrySet().stream()
                .limit(MAX_THREAT_GROUPS)
                .map(entry -> entry.getKey() + (entry.getValue() > 1 ? "x" + entry.getValue() : ""))
                .collect(Collectors.joining(","));

        if (counts.size() > MAX_THREAT_GROUPS) {
            targeting += ",...+" + (counts.size() - MAX_THREAT_GROUPS);
        }

        return " targeting=" + targeting;
    }

    private String formatDistance(double distance) {
        return String.format(Locale.ROOT, "%.1f", distance);
    }
}
