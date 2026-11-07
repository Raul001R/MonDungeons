package sauceology.mondungeon

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item

object DungeonItems {
    val DUNGEON_KEY = Item(Item.Properties())

    fun register() {
        Registry.register(BuiltInRegistries.ITEM, MonDungeons.id("dungeon_key"), DUNGEON_KEY)
    }
}
