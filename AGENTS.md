# AGENTS.md — Raid Dungeon (standalone Cobblemon addon)

Context for AI coding agents. Read this before writing code.

## What this is

A **standalone** Cobblemon addon in its **own repo** — separate from the gym succession mod. A rare **dungeon key** item spawns in chests around the world. Using one opens a UI showing loot odds, then teleports the player into a 10-round NPC trainer gauntlet. Clear all 10 and a spin rolls the key's loot table for a reward, then returns the player to where they started.

**The difficulty is in finding the key, not the dungeon.** The dungeon is the same every run. Better keys don't make a harder dungeon — they give better reward odds.

Ships when the server level cap reaches **50**, so dungeon trainers scale toward level 50 (not constrained by the current 25 cap).

## Stack

- Minecraft 1.21.1, Cobblemon 1.8, Fabric, Kotlin
- Build JVM JDK 25; mod targets Java 21 bytecode
- **Its own mod id and package root** — this is a new project, not the `custom-trainers` repo. Set the id/package when the Fabric template is generated; do not reuse the gym mod's id.

## Relationship to the gym mod

The gym succession mod (separate repo) already solved two things this addon needs: **return-to-origin teleport** (capture position/dimension/yaw/pitch, restore on every exit) and a **`BATTLE_VICTORY` + battle-sequence state machine**. This is a separate codebase, so you can't import that code — but **use it as the reference implementation**: copy the approach, don't reinvent it, and match its failure-path rigor. No hard dependency between the two mods.

## The flow (what a run is)

1. A dungeon key spawns in random chests server-wide (loot-table injection).
2. Player uses the key → opens the Raid Dungeon UI, showing loot odds **for that key's tier**.
3. Start → capture return position, acquire the global lock, teleport into the arena.
4. 10 rounds of NPC trainer battles, progressively harder, mixing singles and doubles, varied types.
5. After round 10, a spin rolls the key's loot table, shows the result, grants the item.
6. Consume the key (only now), return to origin, release the lock.

## Build in stages

Build in order. Each stage ends somewhere testable. **Build and run after each stage before the next.** Stage 6 is the first full playable run; Stage 7 is the hard part.

### Stage 1 — Skeleton
Addon loads, the key item exists.
- New Fabric Kotlin project, Cobblemon 1.8 as a dependency, mod metadata
- Register the **dungeon key** item
- Datapack loot injection so the key spawns in chests
- End state: you can find a key item in a chest. Nothing happens when used yet.

### Stage 2 — Key tiers & data
Multiple key tiers, each mapped to a loot table.
- `DungeonKeyTier` data (id, chest weight, loot table id) — data-driven, not constants
- One loot table per tier; chest weights set so better tiers are rarer
- End state: several key tiers exist in chests at different rarities, each tied to a loot table.

### Stage 3 — Raid UI
Using a key shows its odds.
- Custom GUI opened by using a key
- Reads **that key's loot table** and displays the odds (same table the spin will roll later — one source of truth)
- A **Start** button (wire it up in Stage 4)
- End state: use a key → UI shows the correct odds for that tier.

### Stage 4 — Enter & exit + lock
Teleport in and back, one at a time.
- A single **global lock** — only one run at a time; refuse Start if occupied
- On Start: capture return position (reference the gym mod), acquire lock, teleport to the arena
- A way to end the run that restores the exact return position and releases the lock
- End state: Start sends you to the arena and a test exit returns you to the exact spot. A second player can't start while you're in.

### Stage 5 — The gauntlet
10 rounds of battles.
- Sequence of NPC trainer battles (reuse RCT trainers), scaling toward level 50
- Mix of singles and double battles, varied types — round definitions are **data-driven**
- `BATTLE_VICTORY` handling, filtered by `battleId`, advances the round counter 1 → 10
- End state: Start drops you into round 1; winning advances; clearing round 10 ends the gauntlet.

### Stage 6 — Payout (first full run)
Clear 10, get loot.
- On clearing round 10: roll the key's loot table **server-side first**, then play the spin UI animation
- Grant the reward, **consume the key**, return to origin, release the lock
- End state: a full happy-path run works start to finish and pays out.

### Stage 7 — Failure paths (the hard part)
Doesn't break when a run goes wrong. For each: player **keeps the key**, is returned to origin, lock is released.
- Lose a round battle
- Disconnect / death mid-run (return on reconnect or next login)
- Server stop mid-run (no stuck lock on restart, player not trapped in the arena)
- `BATTLE_VICTORY` doesn't fire on forfeit/flee — handle so a round can't hang
- **The lock MUST release on every one of these.** A leaked lock makes the dungeon permanently unusable — same stuck-state bug class as a gym challenge that never clears.

## Key consumption — on reward, not on start

The key is destroyed **only at the moment the reward is granted** (end of round 10, after the spin). On any failure, the player **keeps the key** and retries. Never consume it at Start — a round-1 crash must not eat it.

## Data model

```
DungeonKeyTier {
    id: String                      // "legendary", "rare", ...
    chestWeight: Int                // rarity in chest loot (lower = rarer)
    lootTableId: ResourceLocation   // the SAME table the UI reads and the spin rolls
}

ActiveDungeon {                     // in-memory only, one at a time
    playerUuid: UUID
    keyTierId: String
    currentRound: Int               // 1..10
    returnPos: BlockPos + dimension + yaw + pitch
}

// a single global lock guarding ActiveDungeon (null = free)
```

Arena location and the 10 round definitions are config/data, not hardcoded.

## Open decisions — ASK, DO NOT GUESS

1. **Do party Pokémon heal between rounds?** No-heal is an attrition gauntlet; heal-between makes each round fresh. Hugely changes difficulty and the Stage 5 loop. Ask before building rounds.
2. **Key tiers:** how many, and their names.
3. **Loot table contents** per tier.
4. **Arena location** — the single shared dungeon's coords/dimension.
5. **Cooldown after a failed run?** One-at-a-time + free retries means a player can camp the slot. Optional short cooldown — decide.

## Cobblemon / technical notes

- **Cobblemon's battle API is barely in model training data — expect hallucinated methods.** Starting NPC battles (and doubles) against defined teams is the hardest, most error-prone part. On a build failure at a Cobblemon or RCT symbol, check the real source in the Gradle dependencies instead of trying variations of a method that never existed.
- Double battles: use Cobblemon/RCT battle formats, don't hand-roll the engine.
- The spin is cosmetic — decide the roll server-side first, then animate.

## Conventions

- Feature branches, PRs, no direct commits to `main`.
- RCT integration stays behind a runtime "is mod loaded" check — no hard dependency.
- Every tuneable value (rounds, odds, chest weights, arena coords, cooldown) lives in config/data, never a constant.

## Session handoff
Read memory.md at the start of each session and update it with decisions, validation results, and remaining work.
