# Agent memory

## Stage 6 CI download failure (2026-11-09)
- User supplied another failing check: Maven Kotlin compiler2.4.20 JAR redirects
  to a GitHub browser release URL returning504. The same Stage6 commit has both
  successful and failing runs, so one green run did not resolve the flaky path.
- Official JetBrains asset587891620 serves the same58593245byte compiler. Its
  published SHA256cf97161430683fb9af96dc6a7017ffae1fe8737b80d12eee11f2fe3e76f8ded8
  matches the working local compiler. CI now prefetches/verifies that artifact,
  fetches its official POM, and uses an init-script exclusive Maven repo for only
  org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20. Other dependencies and
  compiler transitives keep normal resolution. Kotlin version/game code unchanged.
- Validate local repo/init script and observe fresh CI on6 and7 before claiming
  this failure fixed. Apply fix to Stage7 as well (already pushed5486291).
- Official API compiler/POM download completed locally; JAR SHA256 verified.
  Gradle dependencyInsight with the init script PASS (9s), resolving compiler
  and its normal transitive graph. Full init-script build/remote checks pending.
- Run42571132261 retried4times but services.gradle.org returned HTTP504; both
  downloads.gradle.org and GitHub browser download URL also returned504 in
  direct checks. Official GitHub release asset API418398387 responds200 with
  binary archive140320662bytes. CI now fetches via that API, verifies official
  SHA256bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f,
  unpacks and uses that same Gradle9.5.1. Wrapper checksum also pinned. CI sets
  up JDK21+25 explicitly and passes both toolchain env paths to Gradle. New run
  must be observed to completion before claiming CI fixed.
- Stage6 payout pushed as7552804. User reported failed build; read actual Actions
  job129006699783/run42570560870. Gradle distribution download timed out at10s
  with one attempt; no source compilation started. Detailed log captured in
  ignored build/ci-stage-six/job.log in primary workspace.
- Increased wrapper networkTimeout to60000ms, retries3, backoff1000ms. This
  fixes the observed bootstrap weakness without changing game behavior.
- Applied in isolated build/stage-six-ci worktree on feature/stage-six-payout.
  Primary feature/stage-seven-failure-paths contains unfinished journal/death-key
  changes; do not mix these into the Stage6 CI fix. Stage7 must resume afterward.
- Wrapper validation and new CI result pending below.
- gradlew.bat --version PASS: Gradle9.5.1 on JDK25; local cached distribution
  used, so CI remains the fresh-download verification.

## Mega Showdown source lookup (2026-11-09)
- User asked to obtain the item names from the mod instead of asking them to
  list everything. Read AGENTS.md/memory.md and consulted the official upstream
  yajatkaul/CobblemonMegaShowdown, commit06b995dcf8ed8d0e51a65e206865838a184daeee.
  gradle.properties identifies1.3.3+1.8.4+1.21.1-release, matching our MC/Cobblemon.
- Found official generated data/mega_showdown/tags/item/mega_stone.json (86
  members) and z_crystal.json (35). Actual item registration source is
  common/src/main/java/com/github/yajatkaul/mega_showdown/item/MegaShowdownItems.java.
  Reference snapshots saved under ignored build/mega-showdown-reference.
- Replaced both TODO set entries with itemTag mega_showdown:mega_stone and
  mega_showdown:z_crystal, in bundled and existing local run config. Tags use
  actual installed membership and allow datapack additions; no names guessed.
  Counts/weights/odds unchanged. Both JSON files parse, tags and weights checked.
- This supersedes prior pending member-list requests. No TODO reward entries
  remain in bundled JSON. Addon providers are still absent locally; source
  lookup is not a live registry/playthrough verification. Actual pack must pass
  /monDungeons checkrewards. README updated. Jar rebuild pending.
- Also migrated only the older verified Fossil/Poke Snack TODO fields in the
  existing local test config to match the previously resolved bundled definitions;
  preserved counts, weights, odds and other settings. No local TODO remains.
- Build --offline PASS (43s), fast admission/round checks PASS. Inspected remapped
  jar's dungeon-loot.json: both official tags present and no TODO placeholders.
  Output build/libs/mondungeons-1.0.0.jar. No new Minecraft runtime run needed for
  this data-only change; addon runtime registry validation remains outstanding.

## User-supplied reward IDs (2026-11-09)
- Follow-up user confirmed Tera Shards follow mega_showdown:<type>_tera_shard,
  Gold Bottle Cap is obc:bottle_cap_gold, Silver Bottle Cap is obc:bottle_cap.
  Applied to bundled and existing local test JSON. Tera entry now chooses among
  18 standard type shards, uniformly; count2/weight8 and all group odds unchanged.
  Both JSON files parse and shard counts/weights were checked. These are supplied
  IDs, still not verified against the absent addon registries. No jar rebuild.
  Only Mega Stone and Z-Crystal member definitions remain TODO. Their naming
  patterns are recorded below; exact member lists/tags are still requested.
- Re-read AGENTS.md and memory.md. User supplied mega_showdown:wishing_star
  and legendarymonuments:golem_scrap; applied both to bundled dungeon-loot.json.
  These are user-confirmed, not runtime-verified here (providers absent).
- Mega Stones follow mega_showdown:<stone name>, Z-Crystals
  mega_showdown:<name>_z per user. These are naming patterns, not a complete
  registry member list; preserve set placeholders until exact lists/tags arrive.
- Last supplied value was just mega_showdown: (incomplete). Asked whether this
  was for Tera Shards, for the complete ID/pattern, and for both bottle-cap IDs.
  Also requested stone/crystal member lists or tags; do not invent item suffixes.
- Counts, weights and odds unchanged. Stage6 still needs actual-pack validation;
  no new build/runtime claims for this data-only update.
- Applied the same two exact placeholder replacements to the existing local
  run/config/mondungeons/dungeon-loot.json, preserving all other owner settings.
  Both JSON files parse successfully and contain the supplied IDs. No jar rebuild.

## Stage 6 payout (2026-11-08, in progress)
- User asked whether Stage6 was done, then approved completing it first. Stage7
  is paused as a task switch; no Stage7 source changes were made. Stage5 was
  committed/pushed as c1daa2f to feature/stage-five-gauntlet. Current working
  branch feature/stage-six-payout, created from that commit.
- Re-read AGENTS.md and memory.md. One selection per clear remains the design;
  an item selection can grant its configured stack. Keep free failed-run retries.
- Implementing server-side weighted selection from the exact menu table, concrete
  Pokemon/shiny creation, S2C cosmetic spin, server timer, grant then one-key
  consumption, origin restoration and lock release. No client claim/roll packet.
- Seven TODO item IDs remain unresolved. Asked for exact IDs/providers; do not
  replace/remove prizes or reweight odds. Validate every positive-probability
  reward before admission and reject unavailable tables while retaining the key.
- Build/runtime checks pending. Do not claim Stage6 shipped until validation and
  reward configuration are resolved. Stage7 crash-transaction/death hardening
  remains future work.
- User clarified Mega Stones, Z-Crystals and Tera Shards are item SETS: added
  mutually exclusive item/items/itemTag forms, uniform choice within a prize,
  unchanged entry/group probability. Golem Scrap and Tera Shard must stay TODO.
  Search namespaces mega_showdown, zamega, cobblemon and cobblefurnies. JEI and
  Mega Showdown/Zamega are absent from this dev runtime/cache, so do not claim
  JEI confirmation. Cached Cobblefurnies jar contains no matching snack/scrap/
  fossil/wishing entries. Actual Cobblemon registry confirms poke_snack and all
  15 fossil IDs; bundled config now uses those, fossils as a random set.
- Actual registry also rejects draft cobblemon:gold_bottle_cap and
  cobblemon:silver_bottle_cap. These need exact installed-pack IDs too. Other
  configured non-TODO items validated. Admin /monDungeons checkrewards reports
  all unavailable entries with labels. Existing owner config is never replaced.
- Initial build PASS and Minecraft suite5/5 PASS at21:37:50. Payout test checks
  deterministic probability intervals, item-set/tag validation, item stacks,
  exact one-key consumption, duplicate grant refusal, full inventory, offhand
  and random shiny Pokemon. Follow-up PC overflow check and final UI/timer
  changes are being validated; no full human playthrough claimed.
- Follow-up Minecraft suite5/5 PASS at21:41:04 including full-party -> PC shiny
  delivery. Snapshot build/stage-six-payout-tests.log. Native ten-round tests
  still use synthetic victory events; payout delivery tests are separate from
  that gauntlet test. Do not claim a complete human or end-to-end network run.
- Final reward screen visually checked at854x480. Purple/gold rounded panel,
  easing card reel, center prize marker, claimed state and Esc hint fit. Snapshot
  build/stage-six-reward-screen.png; client log stage-six-reward-preview.log.
  Only result preview inspected; no user world opened. Preview22130 stopped.
- Reward lock stays held through spin, delivery and return; finished payload is
  sent after teleport. Missing key/full inventory fails before mutation. Grant
  objects refuse a second delivery. Unexpected errors have a separate message
  rather than incorrectly claiming the key was kept after a partial exception.
- Final build pending below. No Stage6 commit/push requested or performed yet.
  Live readiness remains blocked by five TODO prize definitions plus two draft
  bottle-cap IDs absent from this runtime. Resolve with the actual pack registry;
  do not silently remove them. Stage7 not resumed.
- Final build --offline PASS (49s); admission/round checks PASS. Remapped jar
  inspection confirms DungeonPayout, RolledDungeonReward, RaidPayoutPayload,
  RaidSpinScreen and bundled dungeon-loot.json. Output build/libs/mondungeons-1.0.0.jar.
  Later changes after the5/5 runtime suite were result-send ordering/error text,
  complete spin duration, a defensive overfull-stack capacity clamp and cosmetic
  reel rendering; these compiled in the final build. End-to-end live payout and
  Stage7 durability/failure checks remain pending, alongside actual-pack IDs.

## Trainer cast, complete rewards and branding (2026-11-08, in progress)
- Re-read AGENTS.md and memory.md. User requested varied NPC looks, all JSON
  prizes represented, then push and start Stage7. Clarified reward request is
  visibility: the random shiny option was missing from the rarity hover. No
  authorization to implement Stage6 payout in this turn; keep that unfinished.
- Replaced standard/Steve appearance with ten RCT-backed trainer skins (Brock,
  Misty, Surge, Erika, Koga, Sabrina, Blaine, Lance, Cynthia, Red boss). Reuses
  installed RCT assets via custom Cobblemon trainer variations; does not copy
  vendor textures or add a hard dependency. Appearance mapping/boss/fallback
  live in dungeon-rounds.json; old configs use bundled mapping defaults.
- Reward payload now includes every group label and every option's label, count,
  kind and exact per-run chance. No take(3) cap on transmitted prizes. Hover
  includes all options, and clickable/focusable View all rows open a paginated
  catalogue. Random shiny Pokemon has an explicit type description there.
  Catalogue keeps server lock updates synchronized with its parent menu.
- User interrupted build159; checked no workspace Gradle wrapper remains running.
  User then requested command/UI branding monDungeons. Root is /monDungeons,
  lowercase /mondungeons alias; removed old /raiddungeon registration. Preserved
  /setdungeon shortcut. Menu title MONDUNGEONS; visible messages/docs renamed.
  This user instruction overrides the UI spec's previous RAID DUNGEON title.
- Need finish compile/integration checks and catalogue visual check, commit/push
  Stage5 branch, then create a Stage7 branch and audit failure paths. Stage6 payout
  is still absent; Stage7 claims must distinguish current gauntlet safety from
  future payout-transaction safety. JSON TODO reward IDs remain unresolved.
- Validation completed: build PASS; RCT integration suite4/4 PASS at20:54:00.
  Checks all ten appearance definitions and installed texture assets, full22-option
  catalogue including random_shiny, packet round trips, command roots/aliases and
  native gauntlet. Snapshot build/stage-five-cast-catalogue-tests.log.
- Actual catalogue UI inspected: all5 Jackpot entries visible including Random
  shiny Pokemon, all7 Rare entries/counts visible, parent MONDUNGEONS branding and
  View all controls work. Screenshots build/mondungeons-jackpot-catalogue.png and
  build/mondungeons-menu-branding.png. No user world opened by preview.
- User chose KEEP FREE RETRIES for Stage7. Do not add a failure cooldown.
- Next: final jar, commit/push Stage5 then Stage7 branch. Read-only audit already
  found vanilla SavedData.save swallows IOExceptions; strict return journaling and
  death-inventory key retention need attention. Do not claim Stage7 complete yet.

## Spec-driven raid menu replacement (2026-11-08)
- Read AGENTS.md, memory.md and user-supplied dungeon-ui-spec.md before edits.
  User rejected the previous item-list/sidebar design. This spec is now the UI
  source of truth; preserve the user's untracked spec file.
- New menu: centered rounded purple panel (~460 GUI units), exact RAID DUNGEON /
  your odds this run title, X, colored key glyph/name/tier, four reward-tier rows
  in Jackpot/Rare/Uncommon/Common order, literal proportional bars, green START
  RUN and round-count footer. Examples are hidden only when compact rows cannot
  fit them. Removed individual-item pagination/sidebar from this screen.
- Reward tables now retain group summaries derived from the SAME key tierOdds
  and rewardTiers used to normalize final item probabilities. UI payload carries
  group chances/examples, actual key display name, round count and occupied state.
  No separate hardcoded odds. Server remains authoritative.
- Added S2C availability payload and broadcasts on global active-lock changes;
  open menu shows disabled DUNGEON IN USE while occupied and re-enables on release.
  Server still revalidates the single-use Start token/key/lock before entry.
- Bars use literal percentages (not eased or given a cosmetic minimum width).
  Palette follows spec hex values. Compact layout prioritizes all four tiers.
- Build PASS; new RCT GameTest suite PASS (3/3 at17:04:04) including source-of-truth
  group odds/examples for every key and packet round trips for free/occupied states.
  Existing native ten-round/healing/boss tests also PASS. Snapshot:
  build/raid-menu-spec-tests.log. No gameplay round definitions changed.
- Actual spec screen visual checks pending. Initial capture found the preview back
  at the title screen; added dev-only F8 reopening and narrowed capture helper to
  the exact Java process with the preview JVM flag. Final preview session10314.
  Also gave title slightly larger typography than subtitle per spec. Do not claim
  final visual verification until recorded below.
- Final visual inspection COMPLETE in Minecraft: compact854x480 and full924x861
  match four-tier layout and palette. Full view shows example names and key note;
  compact view keeps all four rows, X, green Start and footer visible. Toggled
  preview lock and confirmed disabled DUNGEON IN USE. Screenshots:
  build/raid-menu-spec-compact.png, raid-menu-spec-full.png, raid-menu-spec-locked.png.
  Client log build/raid-menu-spec-client.log. Preview session10314 stopped; no
  user world was opened. Two-real-client live broadcast test remains manual.
- Final build --offline PASS (9s); fast admission/round checks PASS. Integration
  suite 3/3 remains PASS from17:04:04; later edits were UI typography/preview only.
  Updated README with new four-group odds and test expectations. Spec file unchanged.
- User asked whether payout is one or multiple rewards. Explained existing design:
  ONE reward selection per clear (group then item); selection may grant a configured
  stack (e.g.4diamonds/2candies). No request to change reward count. Stage6 payout
  is still unimplemented; keep key unchanged in current Stage5.

## Visible trainers and odds screen fixes (2026-11-08)
- Read AGENTS.md and memory.md before implementing. User reported invisible NPCs,
  a blanked-out odds GUI, and requested a more stylish GUI. User reiterated that
  memory.md must be read and updated; this section records current progress.
- Confirmed from real client log: SpawnNPCPacket rejected cobblemon:dummy as an
  unknown NPCClass. Headless battle tests could not catch this client visibility
  issue. Replaced dummy with datapack-registered mondungeons:dungeon_trainer,
  data/mondungeons/npcs/dungeon_trainer.json, using Cobblemon's standard model.
  Names visible, faces challenger, no autonomous interaction/party auto-healing.
- Trainers now spawn visibly, announce challenge, then automatically initiate
  battle after challengeDelayTicks (bundled default40). New setting remains data-
  driven; older configs missing it read the bundled default. Spawn offsets now
  rotate relative to the arena-entry yaw and remain anchored to entry, instead
  of drifting with player position. Pending introductions cancel/remove cleanly.
- Confirmed actual MC1.21.1 Screen.render calls renderBackground. Previous code
  drew odds before super.render, causing another vanilla background/blur to cover
  all content. Reworked to one custom renderBackground via super.render, then
  foreground content. No second vanilla blur pass.
- Redesigned screen: navy panel, tier-color accents/badge, key icon, custom styled
  buttons, hover reward cards, pagination and wide-layout summary sidebar.
  Retains real server odds, complete-label tooltip and single-use Start token.
  Start disabled without a network connection for safe title-screen previews.
- Added guarded development-only visual preview: runClient -PraidUiPreview=legendary
  (or another tier). Opens the real screen at the title screen from local reward
  data, without modifying a world. Installed modpacks never run this preview.
- Validation so far: build/fast checks PASS for gameplay/UI changes; BOTH updated
  RCT GameTests PASS (16:26:24), including registered NPC class, spawn before battle,
  ten delayed challenges, healing schedule and boss setup. Log snapshot intended
  at build/stage-five-visible-trainer-tests.log. Visual preview initially opened
  during loading and was replaced by the title screen; added overlay-null guard.
- Actual client visual inspection COMPLETE at compact854x480 and wide1584x861.
  Confirmed readable rewards/chances, tier badge, custom controls, wide sidebar
  and row tooltip. Screenshots: build/raid-ui-preview.png and
  build/raid-ui-preview-wide.png. Client log: build/raid-ui-preview-client.log.
  Preview session94630 stopped after capture; no user world was opened/modified.
- Final build --offline PASS (7s), admission/round checks PASS. Remapped release
  jar includes registered NPC JSON, updated gauntlet and client screen. Gameplay
  GameTests remain PASS from16:26:24; later change was only preview loading guard.
  Actual in-world NPC visual retest remains for user; registered-class spawn and
  challenge sequencing were verified in integration tests, not a client playthrough.
- Stage5 still on feature/stage-five-gauntlet, uncommitted/unpushed. No Stage6 work.

## /setdungeon shortcut (2026-11-08)
- User requested standing in an area and running /setdungeon to configure it.
- Added operator-only /setdungeon using the same setArena implementation as
  /raiddungeon setarena (which remains supported). Saves current dimension,
  exact position and facing immediately; refuses changes during an active run.
  This sets the entrance point in the existing area; it does not generate a structure.
- README now uses /setdungeon as the primary setup command. Build --offline
  SUCCESS; admission and round checks PASS. In-world command use not retested.

## Stage five gauntlet (2026-11-08)
- Read AGENTS.md and memory.md at session start. User authorized Stage 4 push
  and Stage 5. Verified build/admission checks, committed Stage 4 as 28079f4 and
  pushed origin/feature/stage-four-entry-exit. No main merge. Current branch:
  feature/stage-five-gauntlet. Stage 5 changes remain uncommitted/local.
- User decided: full-party healing EVERY OTHER round (after 2/4/6/8), and a very
  strong, BIG final Pokemon boss. User clarified "mega" means oversized like Rift,
  NOT Mega Evolution. No Mega Showdown or Rift dependency should be introduced.
- Arena remains unconfigured by explicit previous decision. /raiddungeon setarena
  remains setup path. No new arena coordinates have been chosen.
- User requests autonomous work without routine confirmations. Workspace policy
  still requires certain escalations. OpenAI Docs permission guidance checked;
  user can choose Approve for me in composer permissions if available. Do not
  claim the session permission mode was changed by the agent.
- Implementation: dungeon-rounds.json -> config/mondungeons on first attempt,
  reload per attempt. Ten defaults scale32->50; doubles3/6/8, varied RCT teams,
  trainer offset/AI skill/healing/timing in data. Same gauntlet for every key.
- RctDungeonTeams requires runtime isModLoaded("rctmod") then reads installed
  RCT trainer JSON resources. Creates disposable Cobblemon Pokemon/NPC teams;
  no native RCT card/story/bag/payout lifecycle and no RCT class linkage.
  This reuses real RCT teams with selected slots and round-level normalization.
  Verified IDs/teams against cached RCT0.19.2-beta jar before selecting them.
- Boss: Cynthia's slot5 Garchomp from champion_cynthia_03a5, level50, scale3,
  perfect IVs,252HP/252Atk/6Spe,Leftovers,Earthquake/DragonClaw/SwordsDance/StoneEdge.
  Stats/moves/item/size are in data. Size is visual; no invented HP multiplier.
- BattleBuilder.pvn uses real GEN_9_SINGLES/DOUBLES, adjustLevel0,
  cloneParties=false,healFirst=false. No player level normalization or entry heal.
  All round teams preflight before lock/teleport. Attrition persists on odd rounds;
  explicit heals only after configured wins AND after battle cleanup.
- DungeonRoundProgress filters battleId and consumes expected ID before advancing,
  rejecting duplicate/unrelated events. DungeonGauntlet subscribes on construction,
  schedules next round after cleanup/delay, and unsubscribes on shutdown.
  Loss, missing/ended battle without victory, failed launch, exit, death/disconnect
  cancel tracked battle/NPC and release lock through existing return journal.
  Temporary tagged NPC leftovers are removed on ENTITY_LOAD (fresh tag set after
  initial entity load). Stage7 still needs full failure-path audit.
- Stage5 clearing final round returns with key, completion chat, no reward/spin;
  Stage6 payout/consumption not started. Cooldown still undecided.
- Standard build --offline SUCCESS, admission checks and new round checks PASS.
  Round checks cover10 rounds,ID filtering/duplicates,healing2/4/6/8,boss and invalid
  config. Gametest sources compile. Optional -PrctCompat=true development profile
  adds pinned RCT/API/Architectury/ForgeConfig runtime only; none packaged/required.
- Fabric gametest tests inspect all installed teams/boss and use real battle starts
  plus SYNTHETIC victory events for10 transitions/healing. Not a human-played run.
  First RCT run caught declared optional held-item alternatives: e.g. manectite
  followed by expert_belt. Fixed loader to choose first actually registered
  alternative, matching RCT's data fallback approach; no Mega mod required.
  Second runGameTest -PrctCompat=true --offline: BOTH required tests PASS at
  14:28:34 (10.42s test execution). Verified installed RCT team creation/levels,
  scale3 boss/perfect IVs/EVs, real singles/doubles starts, ten sequential rounds,
  synthetic duplicate victories, persistent odd-round injury and even-round heals.
  Base runGameTest is skipped without
  rctCompat. No test helpers are packaged in the mod.
- README updated with Stage5 behavior and validation commands. Continue inspecting
  real dependency bytecode/source on API failures; do not guess symbol variants.
- Human-played battle balance, visual boss size/client rendering, arena entry/return
  multiplayer scenarios and full Stage7 fault injection are still manual/pending.
- Final base build --offline SUCCESS after RCT test run (23s); fast admission and
  round checks both PASS. Release jar build/libs/mondungeons-1.0.0.jar contains
  round config and gauntlet/RCT snapshot loader; no tests or optional vendor classes.
  Headless log preserved as build/stage-five-gametest.log. Test server shut down
  normally; no new interactive client launched. README has runClient with rctCompat
  for local play. Arena is still unconfigured. Stage5 remains uncommitted/unpushed.

## Stage four entry/exit (2026-11-06)
- Read AGENTS.md and memory.md at start; reread gym GymChallenges move/restore
  and GymReturnRecovery before implementing. No reference-repo edits.
- User requested Stage 3 push then Stage 4. Committed 940a4b1 and pushed
  origin/feature/stage-three-raid-ui. No merge to main; no PR created (gh absent).
- Current branch feature/stage-four-entry-exit. Stage 4 changes remain local.
- Explicit user decision: leave arena unconfigured. Bundled dungeon-arena.json
  has arena:null and returnRetryTicks:20; copied to config/mondungeons on server
  start. Never guess arena coords. Admin /raiddungeon setarena saves current
  dimension, exact Vec3, yaw/pitch and applies it immediately. Config otherwise
  loads per server startup. No arena structure/dimension is generated.
- Start now sends C2S single-use token issued with odds screen, bound server-side
  to player, original hand and copied key components. Revalidates held key,
  alive/awake/unmounted/non-spectator state, no current Cobblemon battle, no
  pending return, configured/available/in-bounds arena and free global lock.
  No key consumption. Stale/replayed tokens cannot start another run.
- DungeonRuns is per server; ActiveDungeon (UUID, tier, exact origin, round1)
  is guarded by single DungeonAdmission.active value on server thread. A second
  player is refused; /raiddungeon exit only clears the caller's active run.
- Copied gym approach for overworld SavedData return journal (mondungeons_returns).
  Flush return obligation BEFORE moving. Return resets velocity/fall distance,
  restores dimension/Vec3/yaw/pitch, saves player data THEN forgets obligation.
- Lock clears on failed entry (even failed rollback), exit, disconnect, death
  and server stop. Unresolved returns persist independently and retry for live
  online players at configured cadence; dead/offline players recover on respawn/
  login. Lock is never persisted. Pending return prevents that player's reentry.
  These baseline teleport safeguards exist now; battle-specific Stage 7 failure
  paths remain unimplemented and untested.
- Added dependency-free DungeonAdmissionChecks main in test sources, wired into
  Gradle verifyDungeonAdmission/test/check. Covers save-before-move ordering,
  exclusivity, persistence failure, refused/throwing teleport, rollback exception,
  next-player retry and idempotent cleanup. Gradle test discovery disabled only
  for the absent JUnit engine; JavaExec regression checks actually run.
- Final gradlew.bat build --offline SUCCESS, admission regression checks PASS.
  Jar includes arena config/service/recovery/Start payload; test harness is not
  packaged. README documents arena setup and single/multiplayer/manual tests.
- Runtime smoke launch initialized Stage 4 at 22:17:47 with four tiers/tables;
  client left open (session 69410). Log snapshot: build/stage-four-client.log.
  In-world exact return, two-player contention, reconnect/death/restart recovery
  and config admin command checks still pending; do not claim them tested.
- Stage 5 not started. Healing choice must be asked before rounds. Failure
  cooldown remains undecided. Reward TODO IDs/specs remain unresolved for Stage 6.

## Stage three UI (2026-11-06)
- Read AGENTS.md and memory.md before starting. User authorized pushing Stage 2
  and beginning Stage 3. Stage 2 committed dc94582 and pushed to
  origin/feature/stage-two-key-tiers; remote hash verified. No main merge.
  GitHub CLI is unavailable; no PR created. Review link:
  https://github.com/Raul001R/MonDungeons/pull/new/feature/stage-two-key-tiers
- Current branch feature/stage-three-raid-ui, based on that Stage 2 commit.
- Key use in either hand sends RaidOddsPayload server -> client, using the
  held key's DungeonKeyTier.lootTableId and DungeonLootData.tables entry.
  No client-provided odds, duplicated reward lists, payout or key consumption.
- Payload contains server tier ID, configured reward labels/counts and final
  per-run probabilities. Unknown/untagged keys report a message instead of
  assigning a tier. Client without the payload channel gets an unavailable
  message. No client class references in common sources.
- RaidOddsScreen is a non-pausing custom screen with responsive pagination,
  row hover details, disabled Start (Stage 4), Done/Escape close. Server odds
  apply even with different client reward config. Inventory names still use
  the local config as before. Draft unresolved rewards remain visible by label.
- README includes manual Stage 3 tests. Default Master Ball chance is 0.167%
  for common and 2.5% for legendary. Test both hands, all four tiers, paging,
  resize, closing, key count unchanged, invalid tiers and client/server mismatch.
- Final gradlew.bat build --offline SUCCESS, including client and common
  compilation/remapped jar. Independent config arithmetic check confirms final
  reward probabilities sum to 1 for all four tiers. No automated test suite
  exists. Runtime initialized Stage 3 with four tiers/tables at 21:52:05;
  client left open (session 8083). Log: build/stage-three-client.log.
  In-world use/screen rendering and dedicated-server mismatch tests are pending;
  do not describe those as tested.
- Added .kotlin/ to ignore compiler session artifacts. Updated mod description.
- Stage 3 changes not committed/pushed yet. Stage 4 not started; arena/healing/
  cooldown decisions remain open, and TODO reward IDs/specs need resolution
  before Stage 6. The Start button intentionally has no request/action yet.

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
