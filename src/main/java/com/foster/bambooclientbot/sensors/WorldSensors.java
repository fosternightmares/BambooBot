package com.foster.bambooclientbot.sensors;

import com.foster.bambooclientbot.state.BotState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;

public class WorldSensors {
    private static final double NEARBY_RADIUS = 32.0;
    private static final int MAX_ENTITIES_PER_GROUP = 5;

    public String readNearby(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return "nearby unavailable";
        }

        ClientPlayerEntity player = client.player;
        List<EntityDistance> players = new ArrayList<>();
        List<EntityDistance> hostiles = new ArrayList<>();
        List<EntityDistance> passives = new ArrayList<>();
        List<EntityDistance> items = new ArrayList<>();

        for (Entity entity : client.world.getEntities()) {
            if (entity == player || !entity.isAlive()) {
                continue;
            }

            double distance = player.distanceTo(entity);

            if (distance > NEARBY_RADIUS) {
                continue;
            }

            if (entity instanceof PlayerEntity) {
                players.add(new EntityDistance(entity.getName().getString(), distance));
            } else if (entity instanceof HostileEntity) {
                hostiles.add(new EntityDistance(entityTypeName(entity), distance));
            } else if (entity instanceof PassiveEntity) {
                passives.add(new EntityDistance(entityTypeName(entity), distance));
            } else if (entity instanceof ItemEntity itemEntity) {
                items.add(new EntityDistance(itemName(itemEntity), distance));
            }
        }

        return "players=" + formatGroup(players)
                + " hostiles=" + formatGroup(hostiles)
                + " passives=" + formatGroup(passives)
                + " items=" + formatGroup(items);
    }

    public String readSensorSummary(MinecraftClient client, BotState state) {
        if (client == null || client.player == null || client.world == null) {
            return "sensors unavailable";
        }

        ClientPlayerEntity player = client.player;
        int players = 0;
        int hostiles = 0;
        int passives = 0;
        int items = 0;

        for (Entity entity : client.world.getEntities()) {
            if (entity == player || !entity.isAlive()) {
                continue;
            }

            if (player.distanceTo(entity) > NEARBY_RADIUS) {
                continue;
            }

            if (entity instanceof PlayerEntity) {
                players++;
            } else if (entity instanceof HostileEntity) {
                hostiles++;
            } else if (entity instanceof PassiveEntity) {
                passives++;
            } else if (entity instanceof ItemEntity) {
                items++;
            }
        }

        return "nearbyPlayers=" + players
                + " hostiles=" + hostiles
                + " passives=" + passives
                + " items=" + items
                + " " + lookingAtBlockStatus(state);
    }

    private String formatGroup(List<EntityDistance> entities) {
        if (entities.isEmpty()) {
            return "none";
        }

        return entities.stream()
                .sorted(Comparator.comparingDouble(EntityDistance::distance))
                .limit(MAX_ENTITIES_PER_GROUP)
                .map(entity -> entity.name() + ":" + formatDistance(entity.distance()))
                .collect(Collectors.joining(","));
    }

    private String entityTypeName(Entity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
    }

    private String itemName(ItemEntity itemEntity) {
        return Registries.ITEM.getId(itemEntity.getStack().getItem()).getPath();
    }

    private String formatDistance(double distance) {
        return String.format(Locale.ROOT, "%.1f", distance);
    }

    private String lookingAtBlockStatus(BotState state) {
        if (state.lookedAtBlock() == null) {
            return "lookingAtBlock=none";
        }

        return state.lookedAtBlock().format();
    }

    private record EntityDistance(String name, double distance) {
    }
}
