# BambooBot Development Rules

## Project Goal

BambooBot is a Fabric client bot for Minecraft 1.21.11.

The bot controls a real player account through the Minecraft client.

## Core Architecture

### One Brain Loop

There must be exactly one brain loop responsible for decision making.

Event handlers may:

* Observe Minecraft events.
* Update shared state.

Event handlers may NOT:

* Execute gameplay actions.
* Make autonomous decisions.
* Trigger movement, combat, inventory, or chat behavior directly.

### State Driven

Input systems write state.

The brain loop reads state and decides actions.

Action executors perform actions.

Preferred flow:

```txt
Input
↓
State
↓
Brain
↓
Action
↓
Minecraft
```

## Simplicity First

Always implement the smallest working version.

Do not build future systems early.

Do not add abstractions for features that do not exist yet.

## Current Phase

Only implement the functionality described in the active ticket.

Do not add extra commands or features.

## Code Quality

Prefer:

* Small classes
* Clear naming
* Deterministic behavior
* Minimal dependencies

Avoid:

* Global mutable state
* Hidden background loops
* Duplicate decision logic

## Testing

Every phase must include a verification method.

A feature is not complete until it can be tested.

After completing a ticket:

1. Verify build passes.
2. Create a git commit.
3. Use the commit message specified in the ticket.
4. Leave the working tree clean.
5. Do not push unless explicitly requested.