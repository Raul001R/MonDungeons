# MonDungeons

Standalone Fabric/Kotlin addon for Minecraft 1.21.1 and Cobblemon 1.8.4.

Stage four registers `mondungeons:dungeon_key` and adds a separate key roll to
built-in vanilla `minecraft:chests/*` loot tables. Each newly generated chest has
a default 1% key chance. Already generated loot is unchanged; datapack-replaced
chest tables are respected. Using a tiered key opens the Raid Dungeon odds screen
without consuming it. Start enters the configured arena, one player at a time.
The key uses Minecraft's trial-key texture as a temporary visual.

Build with JDK 25: `./gradlew.bat build`. Development Minecraft launches use JDK 21.
The dependency/toolchain pins match the working custom-trainers reference project.
Output: `build/libs/mondungeons-1.0.0.jar`. Install with Fabric API,
Fabric Language Kotlin and Cobblemon on Minecraft 1.21.1.

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
weights from the same config. The UI reads, and future payout code must read,
`DungeonLootData.tables[tier.lootTableId]`, with each reward's probability equal
to its group chance times its normalized within-group weight. No second rewards
file is maintained. The draft `TODO` IDs and Pokemon specs are preserved without
registry validation; they must be resolved before Stage 6 payout is implemented.

Keys store `dungeon_key_tier` in vanilla `minecraft:custom_data`, giving each tier
a distinct name and preventing different tiers from stacking together. Old,
untagged Stage 1 keys remain generic; no tier is silently assigned to them.
Client and server should use matching tier configs for inventory tier names.
Screen tier and reward odds are sent by the server; the client config does not
control those values. Reward counts appear beside labels; chances are final
per-run chances, including both the reward group chance and item weight.

Manual checks in a development world with cheats:

- `/give @s mondungeons:dungeon_key` verifies registration, name and item model.
- `/give @s mondungeons:dungeon_key[minecraft:custom_data={dungeon_key_tier:"legendary"}]`
  gives a Legendary Dungeon Key. Repeat for common, rare and epic; different
  tiers must not stack and opening a chest with any tier must announce discovery.
- `/loot give @s loot mondungeons:keys/tiers` bypasses the overall 1% chance and
  generates exactly one weighted tier key. Repeated rolls should favor common.
- Right-click a tiered key in either hand: Raid Dungeon should show that tier's
  rewards and odds. Use the page buttons to view all entries;
  Done/Escape closes the screen. The held key and its count must remain unchanged.
- Compare Master Ball chances: common shows about 0.167%, legendary shows 2.5%
  with default data. These follow 1% or 15% jackpot odds times 6/36 item weight.
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
`/raiddungeon setarena` (operator permission level 2). This saves your exact
dimension, X/Y/Z, yaw and pitch to `config/mondungeons/dungeon-arena.json` and
applies immediately. No structure or new dimension is generated. You can also
edit this config and restart; `arena: null` disables entry. `returnRetryTicks`
controls how often unresolved returns are retried (default 20 server ticks).

1. Before configuring, try Start: expect the unconfigured message, no movement,
   no key consumption. The server must remain available for a later attempt.
2. After setting the arena, move elsewhere and note your exact position, facing
   and dimension with F3. Use a key and click Start. You should arrive at the
   saved arena entrance. Use `/raiddungeon exit` to restore the original position,
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
above are still required. No rounds, rewards or key consumption are implemented.
