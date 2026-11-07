package sauceology.mondungeon

import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.NestedLootTable

object DungeonChestLoot {
    private val KEY_DROP = ResourceKey.create(Registries.LOOT_TABLE, MonDungeons.id("inject/dungeon_key"))

    fun register() {
        LootTableEvents.MODIFY.register { key, builder, source, _ ->
            // Preserve replacement datapacks' control over their chest tables.
            if (source.isBuiltin && key.location().namespace == "minecraft" &&
                key.location().path.startsWith("chests/")) {
                builder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(KEY_DROP)))
            }
        }
    }
}
