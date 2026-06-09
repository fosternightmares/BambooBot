# Phase 19.2A Client Damage Source Audit

## Scope

This is an investigation-only phase for Minecraft `1.21.11` with Yarn `1.21.11+build.6` and Fabric API `0.141.4+1.21.11`.

No gameplay behavior, reflexes, controllers, commands, navigation, pathfinding, follow, goto, or auto-eat code was changed.

## Summary

BambooBot can detect that the local client player took damage from client state. The client also receives a vanilla `EntityDamageS2CPacket` containing a damage type and source entity ids, and vanilla reconstructs a `DamageSource` client-side.

The most useful exact client source path is:

```txt
ClientPlayNetworkHandler.onEntityDamage(EntityDamageS2CPacket)
-> EntityDamageS2CPacket.createDamageSource(World)
-> Entity.onDamaged(DamageSource)
-> LivingEntity.onDamaged(DamageSource)
-> LivingEntity.getRecentDamageSource()
```

Damage amount is not part of `EntityDamageS2CPacket`. For the local player, health arrives through:

```txt
ClientPlayNetworkHandler.onHealthUpdate(HealthUpdateS2CPacket)
-> ClientPlayerEntity.updateHealth(float)
```

So damage amount should be calculated from client health decrease:

```txt
damageAmount = previousHealth - currentHealth
```

## Primary Questions

| Question | Available | Client-side | Source class/method | Notes |
| --- | --- | --- | --- | --- |
| Can the client detect when BambooBot takes damage? | yes | yes | `ClientPlayerEntity.updateHealth(float)`, `LivingEntity.hurtTime`, `LivingEntity.lastDamageTaken`, `LivingEntity.getRecentDamageSource()` | Health decrease is authoritative for actual damage amount. `EntityDamageS2CPacket` signals a damage event/source. |
| Is `DamageSource` available on the client? | yes | yes | `EntityDamageS2CPacket.createDamageSource(World)`, `LivingEntity.onDamaged(DamageSource)`, `LivingEntity.getRecentDamageSource()` | Vanilla reconstructs and stores the recent source client-side. |
| Is the attacker entity available on the client? | yes, when loaded/tracked | yes | `DamageSource.getAttacker()`, `EntityDamageS2CPacket.sourceCauseId()`, `World.getEntityById(int)` | Can be `null` if the source id is absent or the entity is not present in the client world. |
| Is the attacker UUID available? | yes, when attacker entity is available | yes | `DamageSource.getAttacker() -> Entity.getUuid()` | Packet carries entity ids, not UUIDs. UUID requires resolving the entity client-side. |
| Is damage type available? | yes | yes | `DamageSource.getType()`, `DamageSource.getTypeRegistryEntry()`, `DamageSource.isOf(DamageTypes.*)`, `EntityDamageS2CPacket.sourceType()` | Covers types like `ARROW`, `TRIDENT`, `FALL`, `LAVA`, `CACTUS`, `DROWN`, `PLAYER_ATTACK`, `MOB_ATTACK`. Entity category such as zombie/skeleton comes from attacker entity type, not the damage type alone. |
| Is there a Fabric API client-side hook/event? | no exact damage-source event found | no | Fabric API jars expose `ServerLivingEntityEvents`, `ClientTickEvents`, `ClientEntityEvents`, `ClientPlayNetworking`; no client living damage callback was found | Fabric has server-side living damage events, but this client bot should not rely on server-side events. |
| Would a mixin be required? | not for polling recent source; yes for exact callback timing | client-side mixin possible | `ClientPlayNetworkHandler.onEntityDamage(EntityDamageS2CPacket)` or `LivingEntity.onDamaged(DamageSource)` | A tick-based sensor/reflex can poll `client.player.getRecentDamageSource()`. A mixin is cleaner if Phase 19.2 needs an immediate local-player damage callback with source and packet metadata. |

## Audit Targets

| Target | Finding |
| --- | --- |
| `ClientPlayerEntity` | Has `updateHealth(float)`, called by `ClientPlayNetworkHandler.onHealthUpdate(HealthUpdateS2CPacket)`. This is the local player's authoritative client health update path. It does not receive a `DamageSource`. |
| `LivingEntity` | Has private `lastDamageSource` and `lastDamageTime`, public `getRecentDamageSource()`, and `onDamaged(DamageSource)`. `getRecentDamageSource()` clears the source after roughly 40 world ticks. |
| `DamageSource` | Client-available class with `getSource()`, `getAttacker()`, `getName()`, `getType()`, `getTypeRegistryEntry()`, `isOf(...)`, and `isIn(...)`. |
| `DamageTypes` | Client-available registry keys include `MOB_ATTACK`, `PLAYER_ATTACK`, `ARROW`, `TRIDENT`, `FALL`, `LAVA`, `CACTUS`, `DROWN`, `ON_FIRE`, `IN_FIRE`, `EXPLOSION`, and others. |
| Fabric API events | No exact client-side living damage event found. Relevant available APIs are general client tick/entity lifecycle/networking events. Server damage events exist but are not appropriate for this client-only source-of-truth rule. |
| Client-side damage callbacks | Vanilla `ClientPlayNetworkHandler.onEntityDamage(EntityDamageS2CPacket)` is the direct packet handler. It resolves the target entity and calls `Entity.onDamaged(DamageSource)`. For living entities, `LivingEntity.onDamaged(DamageSource)` stores the recent source. |
| Local player damage methods | `ClientPlayerEntity.updateHealth(float)` provides amount via health changes. `LivingEntity.getRecentDamageSource()` provides recent source if it was delivered by the damage packet and has not expired. |

## What BambooBot Can Know

| Data | Can know | Best client source | Confidence |
| --- | --- | --- | --- |
| Damage occurred | yes | Health decrease from `ClientPlayerEntity.getHealth()` or `ClientPlayerEntity.updateHealth(float)` | high |
| Damage amount | yes | Health delta: `previousHealth - currentHealth` | high |
| Damage type | yes | `client.player.getRecentDamageSource().getTypeRegistryEntry()` or `DamageSource.isOf(DamageTypes.*)` | high when recent source is present |
| Attacker entity | yes, conditionally | `DamageSource.getAttacker()` | medium-high; depends on source and entity being client-loaded |
| Attacker UUID | yes, conditionally | `DamageSource.getAttacker().getUuid()` | medium-high; only if attacker entity resolves |
| Exact source | yes, conditionally | `LivingEntity.getRecentDamageSource()` or a mixin at `onEntityDamage` / `onDamaged` | high for damage type/source packet data; entity resolution can be null |

## Important Limitations

- `EntityDamageS2CPacket` does not include damage amount.
- `HealthUpdateS2CPacket` includes health, food, and saturation, but no source.
- Exact source and exact amount may arrive through separate client paths. Phase 19.2 should correlate them by time/tick.
- `DamageSource.getAttacker()` and `getSource()` can be `null`, especially for environmental sources or unloaded/unresolved entities.
- Zombie vs skeleton is not a `DamageTypes` distinction by itself. A zombie melee hit is likely `MOB_ATTACK` with attacker entity type `zombie`; a skeleton shot is likely `ARROW` or `MOB_PROJECTILE` with attacker/source entities depending on what the client can resolve.
- `LivingEntity.getRecentDamageSource()` expires after about 40 world ticks.

## Recommendation

Recommend **Option C: Hybrid approach**.

Use client health-drop detection as the authoritative damage occurrence and damage amount signal. Pair that health decrease with exact client-provided `DamageSource` data from `LivingEntity.getRecentDamageSource()` when available.

Suggested Phase 19.2 architecture:

```txt
Client state / packet-updated entity state
|
v
DamageSensor or DamageAwarenessStateUpdater
  - reads current health every main loop/reflex tick
  - detects health decreases
  - reads client.player.getRecentDamageSource()
  - resolves damage type, source entity, attacker entity, attacker UUID when present
|
v
BotState damage diagnostics
|
v
Future BrainLoop decisions
```

If later testing shows tick polling misses source correlation, add a small client-side mixin to capture `DamageSource` immediately:

- Preferred mixin target: `ClientPlayNetworkHandler.onEntityDamage(EntityDamageS2CPacket)` if packet ids and reconstructed source are needed.
- Alternative mixin target: `LivingEntity.onDamaged(DamageSource)` filtered to `MinecraftClient.getInstance().player`.

Do not use server-side Fabric damage events for BambooBot client awareness.

