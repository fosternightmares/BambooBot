# BambooBot Development Rules

## Project Goal

BambooBot is a Fabric client bot for Minecraft Java 1.21.11.

The bot controls a real logged-in Minecraft player account through the official Minecraft client.

---

# Core Architecture

## One Brain Loop

There must be exactly one brain loop responsible for decision making.

Preferred flow:

```txt
Input
↓
State
↓
BrainLoop
↓
Action Executor
↓
Minecraft
```

Event handlers may:

* Observe Minecraft events.
* Parse lightweight input.
* Update shared state.
* Queue requests.

Event handlers must not:

* Execute gameplay actions.
* Send chat responses directly.
* Make autonomous decisions.

---

## State Driven Design

Input systems write state.

The brain loop reads state and decides what should happen.

Action executors perform actions requested by the brain loop.

Sensors are read-only.

Action executors perform Minecraft-side effects.

---

# Architecture Ownership

## One Brain Does Not Mean One File

The One Brain Rule governs decision making.

It does not mean all logic should live in a single class.

Example:

```txt
BrainLoop
↓
ActionExecutor
↓
FollowController
↓
RouteExecutor
↓
MovementRecovery
↓
LookController
```

This is still one brain.

The brain decides.

Controllers execute.

---

## Single Ownership

Each subsystem should have one owner.

Examples:

* FollowController owns follow planning.
* RouteExecutor owns route execution.
* MovementRecovery owns stuck recovery.
* LookController owns yaw/pitch behavior.
* MovementDiagnostics owns movement logging.
* PathPlanner owns route generation.

Avoid duplicate ownership.

Avoid implementing the same behavior in multiple places.

---

## ActionExecutor Responsibilities

ActionExecutor is an orchestrator.

It may:

* Route requests.
* Coordinate controllers.
* Track active actions.
* Aggregate results.
* Report status.

It should not become the owner of:

* Follow planning.
* Route execution.
* Recovery logic.
* Diagnostics.
* Look behavior.
* Path planning.

Controllers own behavior.

ActionExecutor coordinates behavior.

---

## No God Files

Target file sizes:

* 0-300 lines: healthy.
* 300-500 lines: monitor.
* 500-800 lines: refactor candidate.
* 800+ lines: refactor required before new features.

If a file grows beyond these limits, extract functionality into a dedicated subsystem.

Avoid creating another index.js or ActionExecutor-style god file.

---

## Refactor Before Growth

When adding a feature:

* Extend the existing owner if one exists.
* Otherwise create a dedicated owner.
* Avoid placing temporary logic in ActionExecutor.
* Avoid placing temporary logic in BrainLoop.

Temporary code becomes permanent code.

---

## Phase Discipline

A phase should primarily do one of:

* Behavior change.
* Refactor.
* Diagnostics.

Avoid combining all three whenever possible.

Refactors should preserve behavior.

---

# Dynamic Bot Identity

Do not hardcode the bot account name.

When the bot name is needed, derive it from the active Minecraft client player profile.

Ticket examples such as:

```txt
BambooBot status
BambooBot nearby
BambooBot stop
```

are illustrative only.

Implementations must support:

```txt
<botName> status
<botName> nearby
<botName> stop
```

where `<botName>` is the currently logged-in account name.

Support both:

```txt
<botName> command
<botName>, command
```

formats when applicable.

---

# Active Ticket Rule

Only implement the functionality requested in the current ticket.

Do not add additional commands, systems, abstractions, or features unless the ticket explicitly requests them.

---

# Simplicity First

Always implement the smallest working version of the active ticket.

Prefer:

* Small classes.
* Clear naming.
* Deterministic behavior.
* Minimal dependencies.

Avoid building future systems before they are needed.

---

# Command Pipeline

Commands should follow:

```txt
ChatHandler
↓
CommandRequest
↓
BotState
↓
BrainLoop
↓
ActionExecutor
↓
Minecraft
```

Command handlers should not bypass the brain loop.

---

# Sensors

Sensors may read Minecraft client state.

Examples:

* Position.
* Health.
* Food.
* Dimension.
* Inventory.
* Nearby entities.
* Looking-at target.

Sensors must not perform actions.

---

# Actions

Gameplay effects should be executed through action executors.

Examples:

* Sending chat.
* Stopping movement.
* Looking at a target.
* Moving.
* Attacking.
* Opening containers.

The brain loop decides.

Executors perform.

---

# Safety

Fail safely.

If client, player, or world state is unavailable, return a safe fallback instead of crashing.

Avoid uncaught exceptions inside tick handlers and event callbacks.

---

# Testing

Every phase must include verification.

At minimum:

```bash
./gradlew build
```

must pass.

Gameplay features should be verified in Minecraft.

Check latest.log for exceptions after testing.

---

# Git Workflow

After completing a ticket:

1. Run verification.
2. Create a commit.
3. Use the commit message specified in the ticket when provided.
4. Leave the working tree clean.
5. Do not push unless explicitly requested.

---

# Guiding Principle

Build a reliable player bot first.

Add complexity only when the previous layer is proven working.

One brain.

Many workers.

Single ownership.

Deterministic behavior.

Reliable before intelligent.
