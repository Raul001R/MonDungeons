package sauceology.mondungeon

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerPlayer

object DungeonItems {
    val DUNGEON_KEY = object : Item(Properties()) {
        override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
            val stack = player.getItemInHand(hand)
            if (player is ServerPlayer) RaidUi.open(player, stack)
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }

        override fun getName(stack: ItemStack): Component {
            val tier = tier(stack) ?: return super.getName(stack)
            return Component.translatable("item.mondungeons.dungeon_key.tier", Component.literal(tier.id.replaceFirstChar { it.uppercase() }))
        }
    }

    fun tier(stack: ItemStack): DungeonKeyTier? {
        if (!stack.`is`(DUNGEON_KEY)) return null
        val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getString("dungeon_key_tier")
        return DungeonLootData.tiers[id]
    }

    fun register() {
        Registry.register(BuiltInRegistries.ITEM, MonDungeons.id("dungeon_key"), DUNGEON_KEY)
    }
}
