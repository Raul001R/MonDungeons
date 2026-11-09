package sauceology.mondungeon

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class RaidRewardOption(val label: String, val kind: String, val count: Int, val probability: Double) {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidRewardOption> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidRewardOption::label,
            ByteBufCodecs.STRING_UTF8, RaidRewardOption::kind,
            ByteBufCodecs.VAR_INT, RaidRewardOption::count,
            ByteBufCodecs.DOUBLE, RaidRewardOption::probability, ::RaidRewardOption)
    }
}

data class RaidOddsEntry(val id: String, val probability: Double, val examples: List<String>, val options: List<RaidRewardOption>) {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidOddsEntry> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidOddsEntry::id,
            ByteBufCodecs.DOUBLE, RaidOddsEntry::probability,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RaidOddsEntry::examples,
            RaidRewardOption.CODEC.apply(ByteBufCodecs.list()), RaidOddsEntry::options,
            ::RaidOddsEntry)
    }
}

/** The preview and live menu use the exact same complete reward projection. */
object RaidRewardMenu {
    fun entries(table: DungeonRewardTable): List<RaidOddsEntry> = table.groups.map { group ->
        val options = table.rewards.filter { it.groupId == group.id }.map { reward ->
            val data = reward.definition
            val pokemon = data.get("type").asString == "pokemon"
            val spec = if (pokemon) data.get("spec").asString else ""
            val random = Regex("(^|\\s)random(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(spec)
            val shiny = Regex("(^|\\s)shiny=true(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(spec)
            val kind = when { !pokemon -> "item"; random && shiny -> "random_shiny"; random -> "random_pokemon"; shiny -> "shiny_pokemon"; else -> "pokemon" }
            RaidRewardOption(data.get("label")?.asString ?: if (pokemon) spec else data.get("item").asString,
                kind, if (pokemon) 1 else data.get("count").asInt, reward.probability)
        }
        RaidOddsEntry(group.id, group.probability, group.examples, options)
    }
}

data class RaidOddsPayload(val tier: String, val keyName: String, val rewards: List<RaidOddsEntry>,
                           val startToken: String, val occupied: Boolean, val roundCount: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidOddsPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RaidOddsPayload>(MonDungeons.id("raid_odds"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidOddsPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaidOddsPayload::tier,
            ByteBufCodecs.STRING_UTF8, RaidOddsPayload::keyName,
            RaidOddsEntry.CODEC.apply(ByteBufCodecs.list()), RaidOddsPayload::rewards,
            ByteBufCodecs.STRING_UTF8, RaidOddsPayload::startToken,
            ByteBufCodecs.BOOL, RaidOddsPayload::occupied,
            ByteBufCodecs.VAR_INT, RaidOddsPayload::roundCount,
            ::RaidOddsPayload)
    }
}

data class RaidAvailabilityPayload(val occupied: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RaidAvailabilityPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RaidAvailabilityPayload>(MonDungeons.id("raid_availability"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaidAvailabilityPayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL, RaidAvailabilityPayload::occupied, ::RaidAvailabilityPayload)
    }
}
