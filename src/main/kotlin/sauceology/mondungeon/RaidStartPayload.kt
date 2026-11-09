package sauceology.mondungeon

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class RaidStartPayload(val token: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidStartPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RaidStartPayload>(MonDungeons.id("raid_start"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidStartPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidStartPayload::token, ::RaidStartPayload)
    }
}
