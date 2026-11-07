# Agent memory

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
