package sauceology.mondungeon

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

object MonDungeons : ModInitializer {
	const val MOD_ID: String = "mondungeons"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		DungeonItems.register()
		RaidUi.register()
		DungeonRunService.register()
		DungeonChestLoot.register()
		LOGGER.info("MonDungeons stage seven initialized: {} key tiers, {} reward tables", DungeonLootData.tiers.size, DungeonLootData.tables.size)
	}

	fun id(path: String): ResourceLocation
		= ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
}
