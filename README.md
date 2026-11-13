# MonDungeons

Standalone Fabric/Kotlin addon for Minecraft 1.21.1 and Cobblemon 1.8.4.

MonDungeons registers `mondungeons:dungeon_key` and adds a separate key roll to
built-in vanilla `minecraft:chests/*` loot tables. Each newly generated chest has
a default 5% common-key chance (rare 2.083%, epic 1%, legendary 0.25%; one key
maximum per chest roll, about 8.333% for any key). Already generated loot is unchanged; datapack-replaced
chest tables are respected. Using a tiered key opens the MonDungeons odds screen
without consuming it. Start enters a trainer gauntlet in the configured
arenas, one player per area. Areas remain unconfigured until you set them.
When all areas are occupied, Start becomes Join Queue. The next waiting player
gets a reserved slot and a Yes/No prompt before entering.
Common keys have 3 rounds, rare 5, epic 7 and legendary 10. Every run ends with
a boss included in that count (2/4/6/9 regular trainers, then the boss).
Clearing all rounds pays one configured prize after a cosmetic spin and consumes
one key. Unresolved reward IDs must be configured before a live run can start.
The key uses Minecraft's trial-key texture as a temporary visual.

Chest rates are configured in `config/mondungeons/dungeon-chests.json` with
`commonKeyChance` (default `0.05`). Other tiers retain the relative `chestWeight`
ratios in `dungeon-loot.json`. Restart after changing these settings. The new
file is created on upgrades; existing reward/round/arena configs are preserved.
The 1.2.1 chest config uses `schemaVersion: 2`. An older config at the former
default `commonKeyChance: 0.10` is backed up and migrated to `0.05` automatically;
customized older values are preserved. Once upgraded, any explicit rate remains
editable without another automatic change. Restart to apply these settings.
Keys already inside chests or player inventories are never removed or changed.

Already-created world chests that still have an unrolled loot table get the new
roll on their first opening, even in old chunks. Previously opened chests have
lost that loot-table reference; a reload cannot regenerate their original loot.
For a selected previously opened chest, an operator can add one random-tier key
without replacing its contents using `/loot insert X Y Z loot mondungeons:inject/dungeon_key`.
This is a guaranteed admin insertion, not the random world-chest chance. A full
chest needs an empty slot. Do not apply loot-table replacement commands to player storage.

For world-wide recovery, [ChestShare](https://modrinth.com/mod/chestshare/) supports
Fabric 1.21.1 server-side and documents a recovery workflow for already-looted
containers. Generate a separate mirror with the same seed/mods/world generation,
export with `/chestshare export recovery`, then import on the live server with
`/chestshare import recovery`. The author says import skips non-empty containers
by default. Back up first and verify the mirror/export on a test copy. This also
changes structure chests to per-player loot; it is optional and has not been
compatibility-tested with MonDungeons. No additional mod was installed here.

Build with JDK 25: `./gradlew.bat build`. Development Minecraft launches use JDK 21.
The dependency/toolchain pins match the working custom-trainers reference project.
Output: `build/libs/mondungeons-1.2.1.jar`. Install with Fabric API,
Fabric Language Kotlin and Cobblemon on Minecraft 1.21.1.

Commands use `/monDungeons` (`/mondungeons` is a lowercase alias):
`/monDungeons exit` returns from your run (or leaves the queue).
Stand at each entrance and use `/setdungeon north`, `/setdungeon south`, etc.
to save multiple named areas (operator level 2). Each area supports one player
with independent battles and rewards. Give the areas enough physical space for
their players and trainers. Reusing a name updates that area's entrance;
`/setdungeon` without a name sets `default`. `/monDungeons setarena [name]`
does the same thing. `/monDungeons arenas list` lists positions and slot status;
`/monDungeons arenas remove <name>` removes an idle area. Running/reserved areas
cannot be edited or removed. Exact duplicate entry positions are rejected.

The queue is FIFO and requires a key. No teleport or key consumption happens
while waiting. When a slot opens, it is reserved for the next eligible player
until they select **Yes, Enter**, **No, Leave Queue**, or their confirmation
window expires (30 seconds by default). Escape declines. Multiple free slots
can invite successive players at the same time. New arrivals cannot take a
reserved slot or pass waiting players. Decline, timeout, disconnect or death
removes that player and passes the slot on. `/monDungeons queue status` shows
your position; `/monDungeons queue leave` cancels waiting or a reservation.
Acceptance rechecks the key (any inventory slot), party, availability and run
settings, and records the return position at that moment. Invalid offers keep
the key and release the reservation. Restart clears waiting/reservations and
active battles; durable return/payout recovery remains per player.

Version 1.2.1 settings are grouped in `config/mondungeons/dungeon-settings.json`:

```json
{
  "runs": {
    "roundsByTier": { "common": 3, "rare": 5, "epic": 7, "legendary": 10 }
  },
  "trainers": { "regularIvCap": 15, "regularEvMultiplier": 0.5 },
  "leaderboard": {
    "pageSize": 8,
    "pointsByTier": { "common": 1, "rare": 2, "epic": 3, "legendary": 4 }
  },
  "queue": { "confirmationSeconds": 30 }
}
```

Use these commands in the server console; add `/` in game (operator level 2):

```text
mondungeons config get
mondungeons config get runs.roundsByTier.common
mondungeons config set runs.roundsByTier.common 3
mondungeons config set trainers.regularIvCap 15
mondungeons config set trainers.regularEvMultiplier 0.5
mondungeons config set leaderboard.pageSize 8
mondungeons config set leaderboard.pointsByTier.legendary 4
mondungeons config set queue.confirmationSeconds 30
mondungeons config reload
```

`set` validates, saves and applies the new settings. `reload` loads manual file
edits. Invalid changes preserve the last working settings and do not overwrite
the invalid file. Valid file rewrites keep a uniquely named backup; missing settings
are filled without resetting other values. Changes affect future runs; active
runs retain their prepared rounds, stats and healing. Queue timeout changes apply
to new invitations; an existing reservation retains its deadline. If a run length changes
while a key menu is open, Start asks the player to reopen it to see the new length.
Round counts must be 1 through the number of templates (10 by default); there
must be exactly one final boss template. Short runs use the first N-1 regular
round templates followed by that boss, preserving their levels and formats.
Custom key tiers need their own `runs.roundsByTier.<id>` and
`leaderboard.pointsByTier.<id>` entries in the file. Point values must be positive
whole numbers. Missing options in older settings files inherit bundled defaults.

On first upgrade, existing `regularIvCap` and `regularEvMultiplier` from
`dungeon-rounds.json` are copied into the new settings file. The old file is
preserved; future stat changes belong in `dungeon-settings.json`. Detailed round,
roster, move, reward, chest and arena files retain their existing roles and loading
rules; `config reload` reloads only the new settings file.

The key menu's **Leaderboard** button ranks players by points from successful
dungeon clears, with next/previous pages and a Back button. Common wins award
1 point, rare 2, epic 3 and legendary 4 by default, reflecting the different run
lengths. The GUI labels the total **Points**. Credit counts only
after reward delivery; failed or abandoned runs add nothing. Totals are saved by
UUID in the world's `data/mondungeons_leaderboard.dat` and survive restarts. Names
reflect the most recent credited clear; equal totals sort by name and UUID.
Payout recovery retries failed credit saves without awarding points twice. Each
run's point value is saved at admission and survives config changes/restarts.
Existing leaderboard totals are preserved because older saves do not contain
key-tier history; they cannot be retroactively weighted. Pending runs from older
return-journal formats retain their original one-point credit. Old settings files
automatically gain the new per-tier point defaults.

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

The **common reward pool's Random Pokemon** prize uses Cobblemon spawn rarity
for every key tier. It first chooses an occupied world spawn bucket using the
server's loaded `BestSpawner` world bucket weights, then chooses equally among
the implemented species assigned to that bucket. Repeated biome/form spawn
entries do not increase a species' odds; species found in multiple buckets use
their most common bucket. Species without a matching world spawn use the rarest
enabled bucket, so they remain eligible. Empty buckets are omitted and remaining
weights normalized. This uses rarity buckets, not local biome/time conditions or
individual spawn-entry weights.

`randomPokemonSelectionByGroup` in `config/mondungeons/dungeon-loot.json`
defaults to `{ "common": "spawn_rarity" }`. Older configs inherit this default
without being rewritten. Set that map to `{ "common": "uniform" }` to restore
equal species odds. An individual Pokemon reward can override it with
`"speciesSelection": "uniform"` or `"speciesSelection": "spawn_rarity"`.
Other reward groups (including the jackpot shiny prize) keep uniform species
selection. Group odds and the chance of winning the Random Pokemon prize remain
the configured values shown in the UI. Restart after editing the loot config;
spawn datapack reloads are picked up on the next prize selection.

Keys store `dungeon_key_tier` in vanilla `minecraft:custom_data`, giving each tier
a distinct name and preventing different tiers from stacking together. Old,
untagged Stage 1 keys remain generic; no tier is silently assigned to them.
Client and server should use matching tier configs for inventory tier names.
Screen tier and reward odds are sent by the server; the client config does not
control those values. The menu follows `dungeon-ui-spec.md`: four reward tiers,
their configured tierOdds, example labels, and literal proportional odds bars.
The green START RUN button becomes JOIN QUEUE while all slots are running/reserved;
changes are pushed to already-open menus.
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
- Open menus with more players than areas: the remaining buttons should
  switch to JOIN QUEUE when all areas are running or reserved, then back to
  START RUN when an area is free and no older waiter needs it.
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
`/setdungeon <name>` (operator permission level 2). This saves your exact
dimension, X/Y/Z, yaw and pitch to `config/mondungeons/dungeon-arena.json` and
applies immediately. No structure or new dimension is generated. You can also
edit this config and restart; `"arenas": {}` disables entry. Older single-`arena`
configs load as an area named `default`, preserving coordinates/facing and timer
settings; the next arena edit saves the new `arenas` map. `returnRetryTicks`
controls how often unresolved returns are retried (default 20 server ticks).

1. Before configuring, try Start: expect the unconfigured message, no movement,
   no key consumption. The server must remain available for a later attempt.
2. After setting the arena, move elsewhere and note your exact position, facing
   and dimension with F3. Use a key and click Start. You should arrive at the
   saved arena entrance. Use `/monDungeons exit` to restore the original position,
   dimension and facing. Your key must remain. Repeat from another dimension.
3. With one area and two players, have A start and B join the queue. B's exit
   command leaves the queue without releasing A's slot. After rejoining, B gets
   a Yes/No prompt when A exits. With two areas, two players can run together.
4. Change/remove the held key or selected slot while the screen is open: Start
   must refuse if the original hand no longer holds the same key components.
   Reopen the screen for a new attempt. Repeated Start packets cannot start
   multiple runs; each offer token is single-use and bound to its player/hand.
5. Disconnect while inside, then reconnect; expect return to origin. Die inside,
   respawn, and expect return. Stop/restart the server while inside and reconnect;
   expect return. Other players must still be able to enter after cleanup.

Active runs and the queue exist only in memory. A separate overworld SavedData
journal (`mondungeons_returns`) stores return obligations before teleportation,
following the gym reference. Entries are removed only after a successful return
and saving player data. Failed returns remain queued; a player with a pending
return cannot start a new run. Exit, death, disconnect and shutdown release the
player's arena slot. Other active areas continue independently.

`./gradlew.bat build --offline` also runs dependency-free admission checks for
save-before-teleport ordering, per-slot exclusivity, FIFO reservations, failed persistence/teleport/
rollback cleanup, retries and repeated cleanup. In-world/multiplayer checks
above complement the Minecraft integration suite. Keys are consumed only on payout.

Stage 5 gauntlet:

- Install Radical Cobblemon Trainers (`rctmod`) and its dependencies to play.
  This addon still loads without RCT; Start refuses before moving you when
  configured trainer data is unavailable. No RCT classes are statically linked.
- `config/mondungeons/dungeon-rounds.json` is created from the bundled defaults.
  It defines the rounds, levels, formats, RCT trainer IDs, team slots, AI skill,
  trainer offset, healing rounds and transition/recovery timing. It is read for
  each new attempt; no restart is needed for subsequent runs. Key tier changes
  reward odds only; every key uses the same difficulty and encounter pools.
- Version 1.0.1 creates `config/mondungeons/dungeon-trainers.json` on first use,
  including on upgraded servers. Each attempt selects nine distinct types from
  eighteen themes, shuffles eligible team slots, and chooses one of nine giant
  bosses. Levels, team sizes and singles/doubles stay defined by the round config.
  Every selected Pokemon must actually have the announced type; dual types count.
  A wrong-type configured slot refuses Start before acquiring the lock or moving
  the player. Custom fixed rounds can specify `type` for the same validation.
  Set roster `enabled` to false to use fixed configured teams. Dialogue intro,
  defeat and victory lines are editable in the roster file.
- `config/mondungeons/dungeon-moves.json` balances disposable opponents only.
  Regular teams use legal learned/source moves up to 90 base power, at most one
  mild utility move, and no listed oppressive moves or Choice/Life Orb items.
  Attacks with at least 80 base power start with 2 PP (bosses: 4). This limits
  total uses; it is not a turn cooldown. Bosses retain configured strong moves
  and stats. All thresholds, item exclusions and move lists are configurable.
- Each trainer visibly spawns in front of the arena entry, faces you, announces
  a challenge and starts battle automatically after a brief introduction.
  `challengeDelayTicks` controls this delay (default 40 ticks / 2 seconds); older
  configs without the field use the bundled default. Trainer offsets rotate with
  the direction saved by `/setdungeon` and stay anchored to that entry point.
- Rounds use varied RCT-backed trainer looks with a player model matching their
  64x64 skin layout, fixing the previous face UV mismatch. RCT skin resources
  are referenced when available on the client. If RCT is server-only, clients
  use varied built-in Minecraft skins instead. No skins are downloaded from
  player accounts or bundled from RCT.
  `trainerAppearances`, `defaultAppearance`, `bossAppearance` and an optional
  per-round `appearance` are data-configurable; older configs inherit the bundled
  cast. NPCs retain the registered Cobblemon class and existing battle behavior.
- Default template levels rise from 32 to 50. Regular templates 3, 6 and 8 are doubles
  when included by the key's run length. Each run uses
  varied type specialists, with the actual type shown in the round announcement.
  Bring at least two conscious Pokemon for runs containing doubles (one for common).
  A later doubles round also requires two
  conscious Pokemon; an exhausted party ends the attempt without consuming a key.
- In `config/mondungeons/dungeon-settings.json`, `trainers.regularIvCap: 15` caps ordinary
  trainers' inherited IVs without raising lower values; `trainers.regularEvMultiplier: 0.5`
  halves their RCT EV spreads, rounding down (252/252/6 becomes 126/126/3).
  Older configs without these fields use the bundled defaults. Set the cap to 31
  and multiplier to 1.0 to restore inherited RCT stats. Boss IVs/EVs stay configured
  separately and are unaffected by these settings.
- **Full-party healing follows included regular rounds 2, 4, 6 and 8, plus the round
  immediately before each tier's boss.** Legendary also heals after round 9.
  Roster `healBeforeBoss: true` adds the pre-boss heal to
  preserved older round configs. Health, status and PP
  carry over through other rounds. No automatic heal occurs on entry. Victory
  transitions wait for battle cleanup before healing or starting the next round.
- Each tier's final round selects a **Titan** (including Garchomp, Dragonite, Salamence,
  Tyranitar, Metagross, Arcanine, Rhyperior, Snorlax or Gyarados): one level-50
  Pokemon at 3x normal scale with perfect IVs, offensive EVs and Leftovers.
  Size is visual; its battle strength
  comes from level, stats, held item, moves and AI. This is not Mega Evolution or
  a dependency on the Rift mod. Base boss settings are in the round config;
  variant species, moves and EVs are in the roster config.
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
  recovery journal.
- Clearing the final boss rolls one prize on the server from the same table shown in
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

Stage 7 recovery:

- Loss, an explicit forfeit response, an ended/missing battle with no victory,
  disconnect, death and server stop abort the attempt and release its arena
  slot. Failed attempts have free retries through the queue. A restart never
  resumes a battle or restores occupied/reserved slots.
- The overworld `data/mondungeons_returns.dat` journal stores the exact origin,
  one-key snapshot and run ID before entry. Journal/player checkpoints use
  compressed NBT, forced temporary-file writes and atomic replacement; IO errors
  propagate. A failed admission cannot teleport first and save later. Schema1
  return files from earlier stages are migrated when next written.
- Offline/dead players keep a pending return. Join and periodic retries restore
  living players, including after respawn. A missing dimension or failed write
  retains the obligation and prevents that player from starting again; other
  players can use the freed slot. Obligations clear only after the returned
  player's checkpoint is saved.
- Normal death drops protect exactly one matching run key, then transfer it
  across respawn. Other keys and inventory items follow normal death rules.
  `keepInventory`/keep-everything copying does not add another key. Keys the
  player deliberately removed from their inventory are not duplicated as refunds.
- Payout saves its receipt with the changed key/item inventory. Caught checkpoint
  failures roll back inventory, receipt and the created Pokemon. Recovery removes
  an uncommitted Pokemon by UUID and preserves a committed payout. Cobblemon owns
  its separate Pokemon storage backend; atomicity across that backend and player
  files during an abrupt power/process failure inside payout is not guaranteed.
- Current trainers are tracked in memory so delayed entity-load events cannot
  mistake a live challenger for a saved leftover. Restarted leftover trainers
  are discarded. Each live trainer holds a transient entity-ticking chunk ticket,
  released with that trainer; these tickets do not persist across restart.
  Cleanup detaches the session before stopping a battle, so late events cannot
  advance a new run. Native busy-state errors during party recall can delay a
  retry's challenge up to `transitionTimeoutTicks`; other battle-start errors
  abort safely. This adds no failure cooldown and never heals the party on entry.

Run `./gradlew.bat runGameTest -PrctCompat=true --offline` for the optional headless
RCT integration tests. The profile adds pinned RCT 0.19.2-beta, RCT API 0.16.2-beta
and their dependencies only to development runtime; none are packaged or made
mandatory in mod metadata. Tests inspect all 39 roster profiles and nine bosses,
actual form types, legal moves, PP limits, and skin/model dimensions, then
exercise real singles/doubles battle starts with synthetic victory events
through the 3/5/7/10-round runs and check final bosses, pre-boss healing, payouts,
clear counts, persistence, console settings and recovery after a credit-save failure.
The 1.2.0 suite also covers simultaneous arena battles/payouts, FIFO reservations,
declines/timeouts, key changes, disconnect/shutdown cleanup, arena-config migration,
queue packet codecs and weighted common Pokemon prizes. The test server uses a
4 GB heap for concurrent native battles and RCT mock-player synchronization.
They do not simulate a human playing the battles. Normal builds run the dependency-free
admission and round progression checks without installing RCT.

For interactive development play, launch
`./gradlew.bat runClient -PrctCompat=true --offline`, configure your arena with
`/setdungeon`, give yourself a tiered key and a prepared Pokemon party,
then use the key and Start. The arena still needs to be built/configured by you.

To inspect the redesigned odds screen without loading a world, use
`./gradlew.bat runClient -PraidUiPreview=legendary --offline`. This developer-only
preview uses local tier data and does not run in installed modpacks. START RUN
is a visual-only button in preview mode; press L to toggle the Join Queue appearance
and F8 at the title screen to reopen the preview.
Use `-PraidUiPreview=spin` or `-PraidUiPreview=reward` to inspect the cosmetic
spin and final reward screens without a world or actual payout.
Use `-PraidUiPreview=queue` to inspect the Yes/No reservation prompt.
The live screen uses server-sent odds and real lock status. Smaller windows omit
the optional example text if needed, while keeping all four tiers visible.
