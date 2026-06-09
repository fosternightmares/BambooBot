# Phase 18.6N Client-State Source of Truth

## Architecture Rule

When Minecraft already tracks a live value, the Minecraft client is the source
of truth. BambooBot may keep internal state for requests, planning, smoothing,
diagnostics, cached command output, or explicit snapshots, but those values must
not become authoritative copies of live client state.

Examples of Minecraft-owned live state:
- Player position
- Yaw, pitch, head yaw, body yaw
- Health and hunger
- Selected slot and inventory contents
- Dimension
- Movement key state
- Current screen/container state when directly observable

## Audit Summary

### BotState

Duplicated or cached client-derived fields:
- `lookedAtBlock`: cached from `client.crosshairTarget` by `LookingAtBlockSensor`.
  Retained as a sensor snapshot so the brain can queue an action from the latest
  observed target. Minecraft remains authoritative.
- `containerState`: cached from `client.currentScreen` by `ContainerSensor`.
  Retained for status output. `client.currentScreen` remains authoritative for
  actions.
- `containerSnapshot`: explicit snapshot captured by a command from the open
  screen handler. Retained as command output, not live container truth.
- `inventorySnapshot`: explicit or lazily-created snapshot captured from the
  player inventory. Retained as command output/cache, not live inventory truth.
- `activeGotoDistance`: cached route/status display value. Retained because
  `BotState` intentionally does not hold `MinecraftClient`; goto behavior
  recomputes it from `client.player` before writing status.
- `activeGotoRouteIndex`, `activeGotoRouteLength`, `activeFollowRouteIndex`,
  `activeFollowRouteLength`: route execution diagnostics. Retained because
  route state is BambooBot planning state, not Minecraft-owned state.
- `followJump`: BambooBot movement intent/status flag. Retained for diagnostics
  and stuck checks. The actual key state remains `client.options.jumpKey`.
- `autoSwing`, `autoUse`, `autoSneak`, `lastSwingTimeMillis`: BambooBot command
  modes and timing, not Minecraft-owned live state.

### Controllers

`LookController`:
- Reads yaw/pitch/head yaw from the client each rotation.
- Stores only gaze intent as a `Vec3d` and diagnostic smoothing metadata.
- Does not treat cached yaw as authoritative.

`MovementController`:
- Writes movement keys through `client.options`.
- Does not keep authoritative copies of movement key state.

`MovementRecovery`:
- Tracks progress/stuck counters and recovery timers. These are BambooBot
  diagnostics/control-flow state, not Minecraft-owned state.

`SimpleMovementController`:
- Tracks active timed/approach requests and remaining ticks. These are action
  lifecycle values, not Minecraft-owned state.

### Behaviors

`GotoBehavior`:
- Keeps active route, route index, replans, and action lifecycle state. These
  are BambooBot planning/execution state.
- Reads current player position from `client.player` when evaluating movement,
  arrival, and route progress.

`FollowBehavior`:
- Keeps follow action, route, route index, replan timers, and target movement
  planning metadata. These are BambooBot planning/execution state.
- Reads current player and target position from client entities during each
  follow tick.

### Helpers, Diagnostics, and Executors

`RouteExecutor`:
- Uses `ClientPlayerEntity` and current waypoint inputs to compute jump/sprint
  intent.
- Retains jump cooldown as BambooBot timing state.
- Still writes `BotState.followJump`; this is intentionally retained for now,
  but remains a future cleanup candidate because route intent calculation has a
  state side effect.

`MovementDiagnostics`:
- Reads live movement/collision/key fields from `player` and `client.options`
  at log time.
- Stores only log throttling and follow diagnostic metadata.

`InventoryActionExecutor`:
- Reads live inventory directly for captures and when no snapshot exists.
- Existing count/have commands may reuse a stored snapshot. This is intentionally
  retained because changing it would alter inventory command semantics in a
  phase that forbids inventory behavior changes.

`ContainerActionExecutor`:
- Reads live `client.currentScreen` and screen handler when capturing or acting.
- Stored snapshots remain explicit command output only.

## Converted Fields

No fields were converted in this phase. The audit found no low-risk conversion
that preserved the explicit constraints against changing movement, follow, goto,
inventory, container, command parsing, or pathfinding behavior.

## Intentional Retained Duplicates

Retained duplicates are limited to:
- Sensor snapshots used by the brain/action queue.
- Explicit inventory/container snapshots requested by commands.
- Route/action diagnostics and status output.
- Planning, smoothing, cooldown, and recovery state owned by BambooBot.

Future changes should prefer adding small read helpers that query
`MinecraftClient` directly over adding new authoritative fields to `BotState`.
