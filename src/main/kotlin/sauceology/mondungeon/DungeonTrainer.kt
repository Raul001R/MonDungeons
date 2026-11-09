package sauceology.mondungeon

import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.npc.configuration.NPCBattleConfiguration
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2

object DungeonTrainer {
    const val TEMPORARY_TAG = "mondungeons:temporary_trainer"
    fun spawn(player: ServerPlayer, round: DungeonRound, team: List<Pokemon>, plan: DungeonRoundPlan,
              anchor: DungeonPosition = DungeonPosition.capture(player)): NPCEntity {
        val npc = NPCEntity(player.serverLevel())
        try {
            npc.npc = requireNotNull(NPCClasses.getByIdentifier(MonDungeons.id("dungeon_trainer"))) {
                "Dungeon trainer NPC class was not registered"
            }
            npc.forcedResourceIdentifier = net.minecraft.resources.ResourceLocation.parse(round.appearance)
            npc.customName = Component.literal(round.name)
            npc.isCustomNameVisible = true
            npc.skill = round.skill
            npc.battle = NPCBattleConfiguration().apply { healAfterwards = false; canChallenge = false }
            npc.isInvulnerable = true
            npc.isNoAi = true
            npc.setNoGravity(true)
            val radians = Math.toRadians(anchor.yaw.toDouble())
            val x = anchor.position.x + cos(radians) * plan.offsetX - sin(radians) * plan.offsetZ
            val z = anchor.position.z + sin(radians) * plan.offsetX + cos(radians) * plan.offsetZ
            val yaw = Math.toDegrees(atan2(player.z - z, player.x - x)).toFloat() - 90f
            npc.moveTo(x, anchor.position.y + plan.offsetY, z, yaw, 0f)
            npc.yHeadRot = yaw
            npc.yBodyRot = yaw
            npc.party = NPCPartyStore(npc).apply { team.forEachIndexed { index, pokemon -> set(index, pokemon) } }
            check(player.serverLevel().addFreshEntity(npc)) { "Could not spawn dungeon trainer" }
            // Fresh ENTITY_LOAD happens inside addFreshEntity; saved leftovers are discarded on later loads.
            npc.addTag(TEMPORARY_TAG)
            return npc
        } catch (exception: Exception) {
            npc.discard()
            throw exception
        }
    }
}
