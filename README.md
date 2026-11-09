# MonDungeons

Standalone Fabric/Kotlin addon for Minecraft 1.21.1 and Cobblemon 1.8.4.

Stage six registers `mondungeons:dungeon_key` and adds a separate key roll to
built-in vanilla `minecraft:chests/*` loot tables. Each newly generated chest has
a default 1% key chance. Already generated loot is unchanged; datapack-replaced
chest tables are respected. Using a tiered key opens the MonDungeons odds screen
without consuming it. Start enters a ten-round trainer gauntlet in the configured
arena, one player at a time. The arena remains unconfigured until you set it.
Clearing all rounds pays one configured prize after a cosmetic spin and consumes
one key. Unresolved reward IDs must be configured before a live run can start.
The key uses Minecraft's trial-key texture as a temporary visual.

Build with JDK 25: `./gradlew.bat build`. Development Minecraft launches use JDK 21.
The dependency/toolchain pins match the working custom-trainers reference project.
Output: `build/libs/mondungeons-1.0.0.jar`. Install with Fabric API,
Fabric Language Kotlin and Cobblemon on Minecraft 1.21.1.

Commands use `/monDungeons` (`/mondungeons` is a lowercase alias):
`/monDungeons exit` returns from your run and `/monDungeons setarena` sets the
arena as an operator. `/setdungeon` remains the short arena setup command.

Change key odds with a datapack overriding
`data/mondungeons/loot_table/inject/dungeon_key.json`; its `random_chance` condition
is the overall key chance setting. Conditional on finding a key, the default
tier weights are common 60, rare 25, epic 12, legendary 3 (out of 100).

`dungeon-loot.json` is bundled as the default and copied on first launch to
`config/mondungeons/dungeon-loot.json`. Edit that server config and restart to
change tiers, chest weights, reward groups or odds. Invalid weights or odds fail
startup explicitly. `/reload` reloads vanilla injection tables, not this config.

`DungeonKeyTier` maps each configured tier to `mondungeons:rewards/<tier>`.
These are custom dungeon reward tables, not vanilla `/loot` tables: they support
both item and Pokemon definitions. Each table resolves `tierOdds` and group item
weights from the same config. The UI reads the table's `groups` summaries, and
future payout code reads its normalized `rewards`, through
`DungeonLootData.tables[tier.lootTableId]`. Each item's probability equals its
group chance times its normalized within-group weight. No second rewards
file is maintained. The draft `TODO` IDs and Pokemon specs are preserved without
registry validation; they must be resolved before Stage 6 payout is implemented.

Keys store `dungeon_key_tier` in vanilla `minecraft:custom_data`, giving each tier
a distinct name and preventing different tiers from stacking together. Old,
untagged Stage 1 keys remain generic; no tier is silently assigned to them.
Client and server should use matching tier configs for inventory tier names.
Screen tier and reward odds are sent by the server; the client config does not
control those values. The menu follows `dungeon-ui-spec.md`: four reward tiers,
their configured tierOdds, example labels, and literal proportional odds bars.
The green START RUN button becomes disabled DUNGEON IN USE while the global
lock is occupied; changes are pushed to already-open menus.
Every configured reward is sent to the client. Hover a rarity to see its complete
list, or click **View all >** to open its catalogue with stack counts, reward types
and individual per-run chances. Catalogue pagination never truncates the pool;
the Jackpot list includes the random shiny Pokemon entry.

Manual checks in a development world with cheats:

- `/give @s mondungeons:dungeon_key` verifies registration, name and item model.
- `/give @s mondungeons:dungeon_key[minecraft:custom_data={dungeon_key_tier:"legendary"}]`
  gives a Legendary Dungeon Key. Repeat for common, rare and epic; different
  tiers must not stack and opening a chest with any tier must announce discovery.
- `/loot give @s loot mondungeons:keys/tiers` bypasses the overall 1% chance and
  generates exactly one weighted tier key. Repeated rolls should favor common.
- Right-click a tiered key in either hand: MonDungeons should show that tier's
  Jackpot/Rare/Uncommon/Common odds. X/Escape closes the screen. The held key and
  its count must remain unchanged until a reward is granted in a later stage.
- Compare default tier odds: common shows 1/9/30/60%, rare shows 3/17/40/40%,
  legendary shows 15/40/35/10%. These are reward-group chances, not the chance of
  a particular example item. All four rows should fit without pagination.
- Open menus as two players, then have one start: the other's button should
  switch to DUNGEON IN USE, then back to START RUN when the active run ends.
- Untagged or unknown-tier keys report an unrecognized tier without opening a
  misleading odds screen. No key should be consumed.
- On a dedicated server, use different client reward odds and confirm the UI
  still displays the server odds. Restart after changing config.
- `/loot give @s loot mondungeons:inject/dungeon_key` exercises the key drop table
  (normally only 1% of rolls succeed).
- For a deterministic injection check, override the chance to `1.0` in a test
  datapack, `/reload`, then `/loot give @s loot minecraft:chests/simple_dungeon`.
  The loot should include a key along with the ordinary dungeon loot. Restore
  the default pack and reload afterwards. Newly generated dungeon chests use
  the same table.

Read AGENTS.md and memory.md before continuing. Tier names and draft rewards
come from the supplied dungeon-loot.json. Remaining reward registry IDs/specs
must be settled before rewards can be granted.

Opening a chest containing a dungeon key displays **You've found a dungeon key!**
to that player in chat only on first discovery. All newly discovered key stacks
in that chest are marked persistently; reopening or moving those keys to another
chest won't announce them again. Existing unmarked keys announce once after
updating. The tier and other custom data are preserved.

Stage 4 arena setup and checks:

The arena is deliberately **unconfigured** by default, as requested. Start reports
that setup is needed and leaves your position/key unchanged. To configure it,
build your arena, stand at its entrance facing the desired direction, and run
`/setdungeon` (operator permission level 2). This saves your exact
dimension, X/Y/Z, yaw and pitch to `config/mondungeons/dungeon-arena.json` and
applies immediately. No structure or new dimension is generated. You can also
edit this config and restart; `arena: null` disables entry. `returnRetryTicks`
controls how often unresolved returns are retried (default 20 server ticks).

1. Before configuring, try Start: expect the unconfigured message, no movement,
   no key consumption. The server must remain available for a later attempt.
2. After setting the arena, move elsewhere and note your exact position, facing
   and dimension with F3. Use a key and click Start. You should arrive at the
   saved arena entrance. Use `/monDungeons exit` to restore the original position,
   dimension and facing. Your key must remain. Repeat from another dimension.
3. With two players, have player A start. Player B's Start must report occupied.
   Player B's exit command must not release A's lock. After A exits, B can start.
4. Change/remove the held key or selected slot while the screen is open: Start
   must refuse if the original hand no longer holds the same key components.
   Reopen the screen for a new attempt. Repeated Start packets cannot start
   multiple runs; each offer token is single-use and bound to its player/hand.
5. Disconnect while inside, then reconnect; expect return to origin. Die inside,
   respawn, and expect return. Stop/restart the server while inside and reconnect;
   expect return. Other players must still be able to enter after cleanup.

The global active run exists only in memory. A separate overworld SavedData
journal (`mondungeons_returns`) stores return obligations before teleportation,
following the gym reference. Entries are removed only after a successful return
and saving player data. Failed returns remain queued; a player with a pending
return cannot start a new run. Exit, death, disconnect and shutdown release the
active lock. Battle-specific failure handling remains for later stages.

`./gradlew.bat build --offline` also runs dependency-free admission checks for
save-before-teleport ordering, global exclusivity, failed persistence/teleport/
rollback cleanup, retries and repeated cleanup. In-world/multiplayer checks
above are still required. Rewards and key consumption are not implemented yet.

Stage 5 gauntlet:

- Install Radical Cobblemon Trainers (`rctmod`) and its dependencies to play.
  This addon still loads without RCT; Start refuses before moving you when
  configured trainer data is unavailable. No RCT classes are statically linked.
- `config/mondungeons/dungeon-rounds.json` is created from the bundled defaults.
  It defines the rounds, levels, formats, RCT trainer IDs, team slots, AI skill,
  trainer offset, healing rounds and transition/recovery timing. It is read for
  each new attempt; no restart is needed for subsequent runs. Key tier changes
  reward odds only; every key uses this same gauntlet.
- Each trainer visibly spawns in front of the arena entry, faces you, announces
  a challenge and starts battle automatically after a brief introduction.
  `challengeDelayTicks` controls this delay (default 40 ticks / 2 seconds); older
  configs without the field use the bundled default. Trainer offsets rotate with
  the direction saved by `/setdungeon` and stay anchored to that entry point.
- Rounds use ten distinct RCT-backed trainer looks: Brock, Misty, Surge, Erika,
  Koga, Sabrina, Blaine, Lance, Cynthia and Red for the boss. The skin resources
  are referenced from installed RCT, not downloaded from player accounts.
  `trainerAppearances`, `defaultAppearance`, `bossAppearance` and an optional
  per-round `appearance` are data-configurable; older configs inherit the bundled
  cast. NPCs retain the registered Cobblemon class and existing battle behavior.
- Default levels rise from 32 to 50. Rounds 3, 6 and 8 are doubles. Types include
  rock, water, electric, grass, poison, psychic, fire, dragon and a champion team.
  Bring at least two conscious Pokemon. A later doubles round also requires two
  conscious Pokemon; an exhausted party ends the attempt without consuming a key.
- **Full-party healing follows rounds 2, 4, 6 and 8 only.** Health, status and PP
  carry over through other rounds. No automatic heal occurs on entry. Victory
  transitions wait for battle cleanup before healing or starting the next round.
- Round 10 is **Titan Garchomp**, a single level-50 Pokemon at 3x normal scale,
  with perfect IVs, 252 HP / 252 Attack / 6 Speed EVs, Leftovers, Earthquake,
  Dragon Claw, Swords Dance and Stone Edge. Size is visual; its battle strength
  comes from level, stats, held item, moves and AI. This is not Mega Evolution or
  a dependency on the Rift mod. All boss settings are in the round config.
- RCT team definitions are read from the installed mod/datapack resources, then
  instantiated as disposable Cobblemon NPC teams. This reuses RCT's teams without
  granting its story progress, trainer drops or card rewards. No native player
  party is cloned, normalized or level-scaled. Temporary NPCs are removed after
  use and on saved-chunk reload. RCT's ordered held-item alternatives use the
  first registered item, preserving its normal fallback when an optional addon
  such as Mega Showdown is absent.
- Matching battle IDs and a player victory are required to advance. Duplicate
  and unrelated results are ignored. Loss, exit, disconnect, death, ended battles
  without victory, or a failed next-round launch stop the tracked battle, discard
  its NPC, release the lock and return/queue the player through the existing
  recovery journal. Stage 7 remains the full failure-path audit.
- Clearing round 10 rolls one prize on the server from the same table shown in
  the menu, then opens the cosmetic spin. After `spinTicks` (default 100 / five
  seconds), the server delivers the prize, consumes one matching key, returns you
  and releases the lock. Closing the spin cannot reroll or prevent delivery.
- Item rewards grant their configured count; Pokemon rewards enter your party
  or overflow to your PC. `random shiny=true` grants an actual shiny Pokemon
  from Cobblemon's implemented species. It does not restrict the roll to pseudo
  legendaries. A missing key, insufficient inventory space or full Pokemon
  storage fails delivery without consuming the key. Items are never dropped on
  the arena floor as a fallback.

Stage 6 reward configuration:

- Run `/monDungeons checkrewards` as an admin to list every unavailable prize.
  Start refuses an invalid positive-chance table before teleporting or consuming
  anything. Invalid prizes are never silently omitted or replaced.
- Mega Stones use the official `mega_showdown:mega_stone` item tag; Z-Crystals
  use `mega_showdown:z_crystal`. These follow the installed mod's tag members,
  including datapack additions, rather than maintaining guessed item names.
  Verified against Mega Showdown 1.3.3 for MC1.21.1/Cobblemon1.8.4:
  [upstream tags](https://github.com/yajatkaul/CobblemonMegaShowdown/tree/06b995dcf8ed8d0e51a65e206865838a184daeee/common/src/generated/data/mega_showdown/tags/item).
- Wishing Star, Golem Scrap, Tera Shards and bottle caps use owner-supplied addon
  IDs, requiring Mega Showdown, Legendary Monuments and the `obc` provider.
  No TODO prize definitions remain in the bundled JSON. This development profile
  still lacks these provider mods; use `/monDungeons checkrewards` on the actual
  pack to verify all prizes before playing. Old server config files are preserved.
- An item entry accepts exactly one of `"item": "namespace:id"`,
  `"items": ["namespace:first", "namespace:second"]`, or
  `"itemTag": "namespace:tag"` (no leading `#`). Lists/tags choose one unique
  member uniformly after that prize wins, then grant `count` of that member.
  The entry's original weight and group odds stay unchanged. Mega Stones,
  Z-Crystals and Tera Shards use sets, not a single generic item.
- Existing `config/mondungeons/dungeon-loot.json` files are preserved. Update
  their entries explicitly and restart to load changes; bundled JSON changes
  do not overwrite a server owner's configuration.

Run `./gradlew.bat runGameTest -PrctCompat=true --offline` for the optional headless
RCT integration tests. The profile adds pinned RCT 0.19.2-beta, RCT API 0.16.2-beta
and their dependencies only to development runtime; none are packaged or made
mandatory in mod metadata. Tests inspect all installed teams and the oversized
boss, then exercise real singles/doubles battle starts with synthetic victory
events through all ten transitions and check the healing schedule. They do not
simulate a human playing ten battles. Normal builds run the dependency-free
admission and round progression checks without installing RCT.

For interactive development play, launch
`./gradlew.bat runClient -PrctCompat=true --offline`, configure your arena with
`/setdungeon`, give yourself a tiered key and a prepared Pokemon party,
then use the key and Start. The arena still needs to be built/configured by you.

To inspect the redesigned odds screen without loading a world, use
`./gradlew.bat runClient -PraidUiPreview=legendary --offline`. This developer-only
preview uses local tier data and does not run in installed modpacks. START RUN
is a visual-only button in preview mode; press L to toggle the occupied appearance
and F8 at the title screen to reopen the preview.
Use `-PraidUiPreview=spin` or `-PraidUiPreview=reward` to inspect the cosmetic
spin and final reward screens without a world or actual payout.
The live screen uses server-sent odds and real lock status. Smaller windows omit
the optional example text if needed, while keeping all four tiers visible.
