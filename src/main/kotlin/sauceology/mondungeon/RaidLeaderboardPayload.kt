package sauceology.mondungeon

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class RaidLeaderboardEntry(val name: String, val clears: Long) {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidLeaderboardEntry> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidLeaderboardEntry::name,
            ByteBufCodecs.VAR_LONG, RaidLeaderboardEntry::clears, ::RaidLeaderboardEntry)
    }
}

data class RaidLeaderboardPayload(val page: Int, val pageSize: Int, val totalPlayers: Int,
    val entries: List<RaidLeaderboardEntry>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidLeaderboardPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RaidLeaderboardPayload>(MonDungeons.id("leaderboard"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidLeaderboardPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RaidLeaderboardPayload::page,
            ByteBufCodecs.VAR_INT, RaidLeaderboardPayload::pageSize,
            ByteBufCodecs.VAR_INT, RaidLeaderboardPayload::totalPlayers,
            RaidLeaderboardEntry.CODEC.apply(ByteBufCodecs.list(10)), RaidLeaderboardPayload::entries, ::RaidLeaderboardPayload)
    }
}

data class RaidLeaderboardRequest(val page: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidLeaderboardRequest> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RaidLeaderboardRequest>(MonDungeons.id("leaderboard_request"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidLeaderboardRequest> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RaidLeaderboardRequest::page, ::RaidLeaderboardRequest)
    }
}
