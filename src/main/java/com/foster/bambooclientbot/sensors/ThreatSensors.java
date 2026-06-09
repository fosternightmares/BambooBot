package com.foster.bambooclientbot.sensors;

import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ThreatInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;

public class ThreatSensors {
    private static final double THREAT_RADIUS = 32.0;
    private static final Set<EntityType<?>> THREAT_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.CREEPER,
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.WITCH,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.EVOKER,
            EntityType.RAVAGER,
            EntityType.GUARDIAN,
            EntityType.ELDER_GUARDIAN,
            EntityType.PHANTOM,
            EntityType.SILVERFISH,
            EntityType.ENDERMITE,
            EntityType.SLIME,
            EntityType.MAGMA_CUBE,
            EntityType.BLAZE,
            EntityType.GHAST,
            EntityType.HOGLIN,
            EntityType.ZOGLIN,
            EntityType.PIGLIN_BRUTE,
            EntityType.WITHER_SKELETON,
            EntityType.SHULKER,
            EntityType.WITHER,
            EntityType.ENDER_DRAGON,
            EntityType.WARDEN
    );

    public void update(MinecraftClient client, BotState state) {
        if (client == null || client.player == null || client.world == null) {
            state.setThreats(List.of());
            return;
        }

        ClientPlayerEntity player = client.player;
        List<ThreatInfo> threats = new ArrayList<>();

        for (Entity entity : client.world.getEntities()) {
            if (entity == player || !entity.isAlive() || !isThreat(entity)) {
                continue;
            }

            double distance = player.distanceTo(entity);

            if (distance > THREAT_RADIUS) {
                continue;
            }

            threats.add(new ThreatInfo(entityTypeName(entity), distance));
        }

        threats.sort(Comparator.comparingDouble(ThreatInfo::distance));
        state.setThreats(threats);
    }

    private boolean isThreat(Entity entity) {
        return THREAT_TYPES.contains(entity.getType());
    }

    private String entityTypeName(Entity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
    }
}
