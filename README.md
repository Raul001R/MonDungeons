# MonDungeons

Standalone Fabric/Kotlin addon for Minecraft 1.21.1 and Cobblemon 1.8.4.

Stage two registers `mondungeons:dungeon_key` and adds a separate key roll to
built-in vanilla `minecraft:chests/*` loot tables. Each newly generated chest has
a default 1% key chance. Already generated loot is unchanged; datapack-replaced
chest tables are respected. Using a key currently does nothing and consumes nothing.
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
weights from the same config. Future UI and payout code must read
`DungeonLootData.tables[tier.lootTableId]`, with each reward's probability equal
to its group chance times its normalized within-group weight. No second rewards
file is maintained. The draft `TODO` IDs and Pokemon specs are preserved without
registry validation; they must be resolved before Stage 6 payout is implemented.

Keys store `dungeon_key_tier` in vanilla `minecraft:custom_data`, giving each tier
a distinct name and preventing different tiers from stacking together. Old,
untagged Stage 1 keys remain generic; no tier is silently assigned to them.
Client and server should use matching tier configs for tier names; Stage 3 will
need server-authoritative UI data.

Manual checks in a development world with cheats:

- `/give @s mondungeons:dungeon_key` verifies registration, name and item model.
- `/give @s mondungeons:dungeon_key[minecraft:custom_data={dungeon_key_tier:"legendary"}]`
  gives a Legendary Dungeon Key. Repeat for common, rare and epic; different
  tiers must not stack and opening a chest with any tier must announce discovery.
- `/loot give @s loot mondungeons:keys/tiers` bypasses the overall 1% chance and
  generates exactly one weighted tier key. Repeated rolls should favor common.
- Use the key: nothing should happen and the stack should remain.
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
