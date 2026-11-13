package sauceology.mondungeon

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.Commands.argument
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.level.ServerPlayer

object DungeonRunService {
    var runs: DungeonRuns? = null
        private set

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            DungeonSettingsConfig.store.reload()
            runs = DungeonRuns(server)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            try { runs?.close() } finally { runs = null }
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { DungeonTrainer.clearLive() }
        ServerTickEvents.END_SERVER_TICK.register { runs?.tick() }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> runs?.disconnect(handler.player.uuid) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> runs?.rejoin(handler.player) }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity is ServerPlayer) runs?.disconnect(entity.uuid)
        }
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (entity is NPCEntity && DungeonTrainer.TEMPORARY_TAG in entity.tags && !DungeonTrainer.isLive(entity.uuid)) entity.discard()
        }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            fun arenaCommand(name: String) = literal(name).requires { it.hasPermission(2) }.executes { context ->
                if (runs?.setArena(context.source.playerOrException) == true) 1 else 0
            }.then(argument("id", StringArgumentType.word()).executes { context ->
                if (runs?.setArena(context.source.playerOrException, StringArgumentType.getString(context, "id")) == true) 1 else 0
            })
            fun listArenas(source: net.minecraft.commands.CommandSourceStack): Int {
                val descriptions = runs?.arenaDescriptions().orEmpty()
                if (descriptions.isEmpty()) source.sendSuccess({ net.minecraft.network.chat.Component.translatable("message.mondungeons.arena_unconfigured") }, false)
                descriptions.forEach { description -> source.sendSuccess({ net.minecraft.network.chat.Component.literal(description) }, false) }
                return descriptions.size
            }
            dispatcher.register(arenaCommand("setdungeon"))
            val root = dispatcher.register(literal("monDungeons")
                .then(DungeonConfigCommands.tree())
                .then(literal("arenas").requires { it.hasPermission(2) }.executes { listArenas(it.source) }
                    .then(literal("list").executes { listArenas(it.source) })
                    .then(literal("remove").then(argument("id", StringArgumentType.word()).executes { context ->
                        val id = StringArgumentType.getString(context, "id")
                        if (runs?.removeArena(id) == true) {
                            context.source.sendSuccess({ net.minecraft.network.chat.Component.translatable("message.mondungeons.arena_removed", id) }, true)
                            1
                        } else {
                            context.source.sendFailure(net.minecraft.network.chat.Component.translatable("message.mondungeons.arena_remove_failed"))
                            0
                        }
                    })))
                .then(literal("queue").executes { runs?.queueStatus(it.source.playerOrException); 1 }
                    .then(literal("status").executes { runs?.queueStatus(it.source.playerOrException); 1 })
                    .then(literal("leave").executes { context ->
                        val player = context.source.playerOrException
                        if (runs?.leaveQueue(player) == true) 1 else {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.mondungeons.queue_none")); 0
                        }
                    }))
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
                .then(arenaCommand("setarena")))
            dispatcher.register(literal("mondungeons").redirect(root))
        }
    }
}
