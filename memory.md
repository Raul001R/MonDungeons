# Agent memory

## Discovery message correction (2026-11-06)
- User tested chest reinsertion and requested announcement only on first find.
- DungeonKeyDiscovery now marks every unmarked key stack in an opened chest
  with custom_data.dungeon_key_discovered=true, preserving tier/other data.
  Sends one message if any stacks were newly marked and calls setChanged.
  Marker travels with the stack through transfers/splits and persists on save;
  reopening or reinserting a discovered key no longer announces.
- Existing keys lack the marker and announce once after this update. Command
  keys also announce on first chest discovery. No key is consumed.
- Requires game/server restart to load code. Manual retest: open chest with a
  fresh key (one message), reopen (none), move key to another chest (none),
  save/reload and reopen (none); fresh unmarked key still announces.
- Validation: gradlew.bat build --offline SUCCESS (Kotlin/Java compilation and
  remapped jar). No in-world retest performed by agent. Re-read on-disk AGENTS.md
  after user's reminder; memory.md was read before implementing this fix.

## Stage two (2026-11-06)
- User requested Stage 2 on a separate branch using their untracked
  dungeon-loot.json. Created feature/stage-two-key-tiers from stage-one branch;
  no commits/pushes. Preserve/include the supplied JSON in the eventual commit.
- JSON supplies common/rare/epic/legendary, weights 60/25/12/3 and all draft
  reward groups/odds. Added explicit lootTableId fields without changing rewards
  or weights. Original draft lacking that field is also accepted.
- DungeonLootData copies bundled root JSON to config/mondungeons/dungeon-loot.json
  if absent. Reads once per process; edit config and restart (not /reload).
  Validates tier IDs, positive integer chest weights, unique table IDs, odds sum,
  reward types and positive weights/counts. Bad config fails startup.
- One CUSTOM dungeon reward table per tier, indexed by ResourceLocation;
  supports both item and Pokemon definitions. Not vanilla /loot reward tables.
  Final entry probability = group percent / 100 * item weight / group weight sum.
  Future UI and spin MUST use DungeonLootData.tables[tier.lootTableId].
- Draft TODO item IDs/specs remain unverified/unresolved. No payout or Pokemon
  creation implemented. Resolve against installed registries before Stage 6.
- Existing vanilla injection table retains 1% overall chance, now references
  mondungeons:keys/tiers. Fabric loot event adds one weighted pool from loaded
  tier data to that built-in table; datapack replacements remain respected.
- All tiers use existing item ID with custom_data.dungeon_key_tier; different
  tiers do not stack. Item name reflects tier. Old untagged keys stay generic
  with no assigned tier. Chest discovery still detects every tier by item ID.
- Client/server matching configs needed for names; Stage 3 must synchronize
  server-authoritative odds/UI data. No key consumption or UI implemented.
- Validation: final gradlew.bat build --offline SUCCESS; jar inspection confirms
  root config, injection and key-tier tables, compiled data loader. No test suite
  exists. Minecraft runtime initialized 4 tiers and 4 reward tables on first
  smoke launch. Final build also initialized 4 tiers and 4 tables at 21:21:03;
  client left open (session 51878). Log snapshot: build/stage-two-client.log.
- In-world chest injection, visual tier names, stacking and distribution remain
  manual checks; README contains give/loot commands. Do not claim completed.
- Remaining decisions: healing, arena, failure cooldown before relevant stages;
  reward TODOs/specs before payouts. Stage 3 not started.

## Scope and current work (2026-11-05)
- User requested stage one from AGENTS.md and this handoff file; use the other
  project at D:\ProgrammingProjects\custom-trainers-cobblemon as reference.
- Standalone addon: mod ID mondungeons, package sauceology.mondungeon. Never
  import or depend on custom-trainers. RCT will remain optional at runtime.
- Branch feature/stage-one-skeleton. Repository started with almost all template
  files untracked; preserve those files. No commits, pushes or PR requested.
- Stage one only: key item, mod metadata, Cobblemon dependency and chest injection.
  No tiers, UI, teleportation, battles, rewards, or key consumption yet.
- Plain Item uses vanilla trial_key model texture. Default 1% chance per vanilla
  built-in chests/* table generation; separate referenced loot table keeps odds
  datapack controlled. Replaced chest tables and other mods' tables are skipped.
- Data location (MC 1.21 singular directory):
  src/main/resources/data/mondungeons/loot_table/inject/dungeon_key.json.
- Build pins copied from reference's working setup: Gradle 9.5.1, Loom
  1.17-SNAPSHOT, Kotlin 2.4.20, loader 0.19.5, Fabric Kotlin
  1.14.1+kotlin.2.4.20, Fabric API 0.116.17+1.21.1, Cobblemon Modrinth PdOzYHKU
  (1.8.4). Original template had newer, unverified pins. Config cache disabled to
  match reference. Build JVM JDK25, bytecode and game JVM21.
- Dev runs unpack Cobblemon's bundled nested jars, excluding Fabric Kotlin,
  onto JavaExec classpath. Do not package those libraries into this addon.
- Gradle requires approval for writes to C:\Users\junio\.gradle under this sandbox.
  .git writes likewise required approval for branch creation.

## Critical design for later stages
- Difficulty is finding keys. Same 10-round dungeon regardless of key tier;
  improved keys change reward odds. Trainers scale toward level50.
- All adjustable values belong in data/config. UI and reward spin must use the
  SAME tier loot table. Server rolls first; animation is cosmetic.
- One global active-run lock. Every termination must release it. Consume key
  ONLY when granting the final reward; every failed run retains its key.
- Ask user before choosing tier count/names, tier rewards, arena location,
  between-round healing or optional failure cooldown. These do not block stage1.
- Build AND run each stage before starting the next.

## Reference project findings (read sources again before reusing)
- Reference AGENTS.md and memory.md were read; its upgrade to Cobblemon1.8.4
  is implemented. Historical 1.7.3 notes do not describe current source.
- src/main/kotlin/com/jow555/customtrainers/item/GymBadges.kt: real item Registry
  registration against BuiltInRegistries.ITEM, no guessed newer-version APIs.
- challenge/GymChallenges.kt: ReturnPosition holds dimension ResourceKey<Level>,
  exact Vec3, yaw and pitch (not just integer BlockPos). Capture before teleport.
  BATTLE_VICTORY subscription filters event.battle.battleId against active state;
  removal gates duplicate results. Tick/lifecycle handling detects disconnect,
  death, forfeits and ended battles even when victory does not fire.
- challenge/GymReturnRecovery.kt: overworld SavedData recovery journal, flushed
  BEFORE teleport; queued return retries after login/respawn, obligations removed
  only after successful restoration. Active battles themselves are not persisted.
- compat/RctGymBattles.kt and challenge/OfflineGymDefender.kt are future NPC
  starting references. Inspect actual dependency source/bytecode on API errors.
- Reference memory warns pvn adjustLevel does not safely force NPC clone scaling;
  NPC original/effected may be same temporary object. Normalize disposable
  snapshot NPC teams; never mutate players' actual parties. RCT copies differ.
- No modifications made to the reference repository.

## Verification
- Build and runtime results will be appended below. README includes deterministic
  datapack override procedure for verifying chest injection and visual checks.

### Build result
- gradlew.bat build --offline SUCCESS (2026-11-05). Kotlin, Java, client compilation and remapped jar pass. No automated tests exist yet. Jar: build/libs/mondungeons-1.0.0.jar.
- Actual MC1.21.1 Mojang API class is NestedLootTable, not LootTableReference (confirmed with javap).
- Initial overlapping build/client attempts caused a Windows resource-directory lock; run subsequent Gradle build/launch tasks sequentially. Both attempts ended; sequential rebuild passed.
- Jar inspection caught incomplete resources from the initial concurrent task collision. Final build --offline --rerun-tasks SUCCESS; confirmed loot JSON, model, localization and icon present in the remapped jar. Use this final build.

### Runtime result
- Development runClient --offline uses Java21 and successfully initialized MonDungeons and Cobblemon1.8.4; log build/stage-one-client.log (15:17:14). Resource atlases built and Cobblemon assets loading. Client left open, exec session42325, for manual in-world validation.
- Still pending: in-world loot generation/deterministic chance override test and visual key inspection. Do not claim those were completed. README has steps. No stage2 work started.

## Chest discovery message (2026-11-05)
- User requested a message when finding a dungeon key in a chest.
- After a successful ServerPlayer.openMenu, a thin Java mixin calls Kotlin DungeonKeyDiscovery. If the opened ChestMenu contains any dungeon key, send the opening player the localized chat message: You've found a dungeon key!
- Triggers once per opening, after loot generation, including double chests and other vanilla ChestMenu containers (barrels/chest minecarts). Reopening a chest with a key announces again. No message for keys only in the player's inventory, empty chests, failed opens or non-chest menus. No item mutation or consumption.
- Localization: message.mondungeons.dungeon_key_found in en_us.json. Existing running client requires restart to load the new code.
- Validation: gradlew.bat build --offline SUCCESS, including mixin Java/Kotlin compilation and remapped jar. In-world chat trigger not manually exercised.
