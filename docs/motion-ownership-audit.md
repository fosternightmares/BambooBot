# Phase 18.6I Motion Ownership Audit

## Scope

This audit checks direct camera writes and direct movement key writes after the
behavior/control package reorganization.

Search patterns:
- Camera: `yaw`, `pitch`, `headYaw`, `bodyYaw`, `setYaw`, `setPitch`, `setHeadYaw`, `setBodyYaw`
- Movement keys: `forwardKey`, `backKey`, `leftKey`, `rightKey`, `jumpKey`, `sprintKey`, `sneakKey`, `useKey`, `attackKey`, `setPressed`

## Camera Write Locations

All direct camera writes are in the control package.

| File | Lines | Package | Classification | Writes |
| --- | --- | --- | --- | --- |
| `src/main/java/com/foster/bambooclientbot/control/LookController.java` | 37-40 | control | allowed owner | `setYaw`, `setPitch`, `setHeadYaw`, `setBodyYaw` in `rotateToward` |
| `src/main/java/com/foster/bambooclientbot/control/LookController.java` | 51-54 | control | allowed owner | `setYaw`, `setPitch`, `setHeadYaw`, `setBodyYaw` in `rotateForNavigation` |

No camera writes were found in behavior, navigation, helper/util, or actions packages.

## Movement Key Write Locations

All direct movement key writes are in the control package.

| File | Lines | Package | Classification | Writes |
| --- | --- | --- | --- | --- |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 21-23 | control | allowed owner | route forward/jump/sprint in `applyRouteMovement` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 29-31 | control | allowed owner | direct-follow forward/sprint/jump in `applyFollowDirect` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 37-39 | control | allowed owner | recovery forward/jump/sprint in `applyForwardNudge` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 44 | control | allowed owner | forward-only press in `pressForward` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 50-52 | control | allowed owner | manual move jump/sprint/direction in `applyManualMovement` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 58-59 | control | allowed owner | route cleanup jump/sprint in `clearRouteMovement` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 65-69 | control | allowed owner | stop jump/sneak/sprint/attack/use in `stopAll` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 74 | control | allowed owner | use key in `setUse` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 79 | control | allowed owner | sneak key in `setSneak` |
| `src/main/java/com/foster/bambooclientbot/control/MovementController.java` | 83-86 | control | allowed owner | directional key clear in `clearDirectionalKeysRaw` |

No movement key writes were found in behavior, navigation, helper/util, or actions packages.

## RouteExecutor

`RouteExecutor` does not directly control camera or Minecraft movement keys.

It returns `RouteMovement` intent values consumed by `MovementController`.
It does still write movement-related state through `BotState.setFollowJump(...)`
while computing jump decisions. This is not a direct key or camera write, but it
is a recommended ownership follow-up because the actions package is still
recording movement state during route intent calculation.

## Recommended Ownership Violations

No direct camera or movement key ownership violations were found.

Recommended follow-up:
- Consider moving `RouteExecutor` out of the actions package or removing
  `BotState.setFollowJump(...)` side effects from route intent calculation in a
  later behavior-preserving refactor.

## Verification

`./gradlew build` passed.
