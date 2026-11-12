package sauceology.mondungeon

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.InteractionHand

object RaidUi {
    fun register() {
        PayloadTypeRegistry.playS2C().register(RaidOddsPayload.TYPE, RaidOddsPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RaidAvailabilityPayload.TYPE, RaidAvailabilityPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RaidPayoutPayload.TYPE, RaidPayoutPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RaidStartPayload.TYPE, RaidStartPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RaidLeaderboardPayload.TYPE, RaidLeaderboardPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RaidLeaderboardRequest.TYPE, RaidLeaderboardRequest.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(RaidLeaderboardRequest.TYPE) { payload, context ->
            context.server().execute {
                val leaderboard = DungeonRunService.runs?.leaderboard ?: return@execute
                if (ServerPlayNetworking.canSend(context.player(), RaidLeaderboardPayload.TYPE)) {
                    ServerPlayNetworking.send(context.player(), leaderboard.page(payload.page, DungeonSettingsConfig.current.leaderboardPageSize))
                }
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(RaidStartPayload.TYPE) { payload, context ->
            context.server().execute { DungeonRunService.runs?.start(context.player(), payload.token) }
        }
    }

    fun open(player: ServerPlayer, stack: ItemStack, hand: InteractionHand) {
        val tier = DungeonItems.tier(stack)
        if (tier == null) {
            player.sendSystemMessage(Component.translatable("message.mondungeons.unknown_key_tier"))
            return
        }
        if (!ServerPlayNetworking.canSend(player, RaidOddsPayload.TYPE)) {
            player.sendSystemMessage(Component.translatable("message.mondungeons.ui_unavailable"))
            return
        }
        // The exact table selected by the held key is also the future payout source.
        val table = DungeonLootData.tables.getValue(tier.lootTableId)
        val entries = RaidRewardMenu.entries(table)
        val rounds = try { DungeonSettingsConfig.current.plan(tier.id, DungeonRounds.load()).rounds.size }
        catch (exception: Exception) {
            player.sendSystemMessage(Component.translatable("message.mondungeons.trainers_unavailable"))
            return
        }
        val token = DungeonRunService.runs?.offer(player, hand, stack) ?: return
        ServerPlayNetworking.send(player, RaidOddsPayload(tier.id, stack.hoverName.string, entries, token,
            DungeonRunService.runs?.active != null, rounds))
    }
}
