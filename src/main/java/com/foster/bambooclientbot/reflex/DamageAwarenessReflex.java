package com.foster.bambooclientbot.reflex;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.state.BotState;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

public class DamageAwarenessReflex implements Reflex {
    @Override
    public String name() {
        return "damage_awareness";
    }

    @Override
    public boolean isEnabled(BotState state) {
        return true;
    }

    @Override
    public void tick(MinecraftClient client, BotState state, ActionExecutor actions) {
        if (client == null || client.player == null) {
            return;
        }

        DamageSource damageSource = client.player.getRecentDamageSource();
        state.observeDamage(client.player.getHealth(), damageType(damageSource), entityLabel(sourceEntity(damageSource)),
                entityLabel(attackerEntity(damageSource)), attackerUuid(damageSource));
    }

    private String damageType(DamageSource damageSource) {
        if (damageSource == null) {
            return "unknown";
        }

        RegistryEntry<DamageType> typeEntry = damageSource.getTypeRegistryEntry();
        return typeEntry.getKey()
                .map(RegistryKey::getValue)
                .map(Object::toString)
                .orElse(damageSource.getName());
    }

    private Entity sourceEntity(DamageSource damageSource) {
        return damageSource == null ? null : damageSource.getSource();
    }

    private Entity attackerEntity(DamageSource damageSource) {
        return damageSource == null ? null : damageSource.getAttacker();
    }

    private String entityLabel(Entity entity) {
        if (entity == null) {
            return "none";
        }

        return EntityType.getId(entity.getType()) + ":" + entity.getNameForScoreboard();
    }

    private UUID attackerUuid(DamageSource damageSource) {
        Entity attacker = attackerEntity(damageSource);
        return attacker == null ? null : attacker.getUuid();
    }
}
