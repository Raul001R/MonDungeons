package sauceology.mondungeon

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** A zero duration dismisses only the prompt carrying the same server-owned token. */
data class RaidQueuePromptPayload(val token: String, val seconds: Int, val keyName: String, val rounds: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidQueuePromptPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RaidQueuePromptPayload>(MonDungeons.id("queue_prompt"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidQueuePromptPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidQueuePromptPayload::token, ByteBufCodecs.VAR_INT, RaidQueuePromptPayload::seconds,
            ByteBufCodecs.STRING_UTF8, RaidQueuePromptPayload::keyName, ByteBufCodecs.VAR_INT, RaidQueuePromptPayload::rounds,
            ::RaidQueuePromptPayload)
    }
}

data class RaidQueueReplyPayload(val token: String, val accept: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidQueueReplyPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RaidQueueReplyPayload>(MonDungeons.id("queue_reply"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidQueueReplyPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidQueueReplyPayload::token, ByteBufCodecs.BOOL, RaidQueueReplyPayload::accept,
            ::RaidQueueReplyPayload)
    }
}
