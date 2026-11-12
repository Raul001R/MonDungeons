package sauceology.mondungeon

import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.NestedLootTable
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.functions.SetCustomDataFunction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition

object DungeonChestLoot {
    private val KEY_DROP = ResourceKey.create(Registries.LOOT_TABLE, MonDungeons.id("inject/dungeon_key"))

    fun register() {
        val tiers = DungeonLootData.tiers.values
        val chance = DungeonChestSettings.load(tiers)
        LootTableEvents.MODIFY.register { key, builder, source, _ ->
            if (key.location() == MonDungeons.id("keys/tiers")) {
                val pool = LootPool.lootPool()
                tiers.forEach { tier ->
                    val tag = CompoundTag()
                    tag.putString("dungeon_key_tier", tier.id)
                    pool.add(LootItem.lootTableItem(DungeonItems.DUNGEON_KEY)
                        .setWeight(tier.chestWeight)
                        .apply(SetCustomDataFunction.setCustomData(tag)))
                }
                if (source.isBuiltin) builder.withPool(pool)
            }
            // Preserve replacement datapacks' control over their chest tables.
            if (source.isBuiltin && key.location().namespace == "minecraft" &&
                key.location().path.startsWith("chests/")) {
                builder.withPool(LootPool.lootPool().`when`(LootItemRandomChanceCondition.randomChance(chance))
                    .add(NestedLootTable.lootTableReference(KEY_DROP)))
            }
        }
    }
}
