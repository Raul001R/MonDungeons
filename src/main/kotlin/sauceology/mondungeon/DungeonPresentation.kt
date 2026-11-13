package sauceology.mondungeon

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer

/** Network presentation is separate from the server-owned payout lifecycle. */
interface DungeonPresentation {
    fun available(player: ServerPlayer): Boolean
    fun show(player: ServerPlayer, payload: RaidPayoutPayload)
    fun queueAvailable(player: ServerPlayer): Boolean = false
    fun prompt(player: ServerPlayer, payload: RaidQueuePromptPayload) = Unit
}

object NetworkDungeonPresentation : DungeonPresentation {
    override fun available(player: ServerPlayer) = ServerPlayNetworking.canSend(player, RaidPayoutPayload.TYPE)
    override fun show(player: ServerPlayer, payload: RaidPayoutPayload) = ServerPlayNetworking.send(player, payload)
    override fun queueAvailable(player: ServerPlayer) = ServerPlayNetworking.canSend(player, RaidQueuePromptPayload.TYPE)
    override fun prompt(player: ServerPlayer, payload: RaidQueuePromptPayload) = ServerPlayNetworking.send(player, payload)
}
