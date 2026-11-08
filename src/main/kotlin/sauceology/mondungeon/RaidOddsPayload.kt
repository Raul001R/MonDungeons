package sauceology.mondungeon

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class RaidOddsEntry(val label: String, val probability: Double) {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidOddsEntry> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidOddsEntry::label,
            ByteBufCodecs.DOUBLE, RaidOddsEntry::probability,
            ::RaidOddsEntry
        )
    }
}

data class RaidOddsPayload(val tier: String, val rewards: List<RaidOddsEntry>, val startToken: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidOddsPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<RaidOddsPayload>(MonDungeons.id("raid_odds"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidOddsPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidOddsPayload::tier,
            RaidOddsEntry.CODEC.apply(ByteBufCodecs.list()), RaidOddsPayload::rewards,
            ByteBufCodecs.STRING_UTF8, RaidOddsPayload::startToken,
            ::RaidOddsPayload
        )
    }
}
