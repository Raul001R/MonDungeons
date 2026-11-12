package sauceology.mondungeon

import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

object DungeonConfigCommands {
    fun tree() = literal("config").requires { it.hasPermission(2) }
        .then(literal("get").executes { context ->
            report(context.source) { DungeonSettingsConfig.store.get(null) }
        }.then(argument("path", StringArgumentType.word()).suggests { _, builder ->
            SharedSuggestionProvider.suggest(DungeonSettingsConfig.store.paths(), builder)
        }.executes { context ->
            report(context.source) { DungeonSettingsConfig.store.get(StringArgumentType.getString(context, "path")) }
        }))
        .then(literal("set").then(argument("path", StringArgumentType.word()).suggests { _, builder ->
            SharedSuggestionProvider.suggest(DungeonSettingsConfig.store.paths(), builder)
        }.then(argument("value", StringArgumentType.greedyString()).executes { context ->
            report(context.source) {
                val path = StringArgumentType.getString(context, "path")
                DungeonSettingsConfig.store.set(path, StringArgumentType.getString(context, "value"))
                "$path = ${DungeonSettingsConfig.store.get(path)}. Saved; applies to new runs."
            }
        })))
        .then(literal("reload").executes { context ->
            report(context.source) {
                DungeonSettingsConfig.store.reload()
                "Dungeon settings reloaded. Applies to new runs; active runs keep their starting settings."
            }
        })

    private fun report(source: CommandSourceStack, action: () -> String): Int = try {
        val message = action()
        source.sendSuccess({ Component.literal(message) }, false)
        1
    } catch (exception: Exception) {
        source.sendFailure(Component.literal("Dungeon settings unchanged: ${exception.message ?: "Invalid config"}"))
        0
    }
}
