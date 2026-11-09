package sauceology.mondungeon

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C only. There is deliberately no client claim/roll packet. */
data class RaidPayoutPayload(val label: String, val group: String, val ticks: Int,
                             val state: String, val candidates: List<String>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidPayoutPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RaidPayoutPayload>(MonDungeons.id("payout"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidPayoutPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidPayoutPayload::label,
            ByteBufCodecs.STRING_UTF8, RaidPayoutPayload::group,
            ByteBufCodecs.VAR_INT, RaidPayoutPayload::ticks,
            ByteBufCodecs.STRING_UTF8, RaidPayoutPayload::state,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RaidPayoutPayload::candidates,
            ::RaidPayoutPayload)
    }
}
