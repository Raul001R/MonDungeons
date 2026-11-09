package sauceology.mondungeon

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.Commands.literal
import net.minecraft.server.level.ServerPlayer

object DungeonRunService {
    var runs: DungeonRuns? = null
        private set

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server -> runs = DungeonRuns(server) }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            try { runs?.close() } finally { runs = null }
        }
        ServerTickEvents.END_SERVER_TICK.register { runs?.tick() }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> runs?.disconnect(handler.player.uuid) }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity is ServerPlayer) runs?.disconnect(entity.uuid)
        }
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (entity is NPCEntity && DungeonTrainer.TEMPORARY_TAG in entity.tags) entity.discard()
        }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(literal("setdungeon").requires { it.hasPermission(2) }.executes { context ->
                if (runs?.setArena(context.source.playerOrException) == true) 1 else 0
            })
            val root = dispatcher.register(literal("monDungeons")
                .then(literal("checkrewards").requires { it.hasPermission(2) }.executes { context ->
                    val problems = DungeonLootData.tables.values.flatMap { table ->
                        DungeonPayout.problems(table).map { "${table.id}: $it" }
                    }
                    if (problems.isEmpty()) context.source.sendSuccess({ net.minecraft.network.chat.Component.literal("All dungeon prizes are available.") }, false)
                    else problems.forEach { context.source.sendFailure(net.minecraft.network.chat.Component.literal(it)) }
                    if (problems.isEmpty()) 1 else 0
                })
                .then(literal("exit").executes { context ->
                    if (runs?.exit(context.source.playerOrException) == true) 1 else 0
                })
                .then(literal("setarena").requires { it.hasPermission(2) }.executes { context ->
                    if (runs?.setArena(context.source.playerOrException) == true) 1 else 0
                }))
            dispatcher.register(literal("mondungeons").redirect(root))
        }
    }
}
