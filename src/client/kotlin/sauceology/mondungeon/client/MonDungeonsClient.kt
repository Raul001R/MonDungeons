package sauceology.mondungeon.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import sauceology.mondungeon.RaidOddsPayload

object MonDungeonsClient : ClientModInitializer {
	override fun onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(RaidOddsPayload.TYPE) { payload, context ->
			context.client().setScreen(RaidOddsScreen(payload))
		}
	}
}
