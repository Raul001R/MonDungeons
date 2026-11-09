package sauceology.mondungeon.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import sauceology.mondungeon.RaidOddsPayload
import sauceology.mondungeon.RaidAvailabilityPayload

object MonDungeonsClient : ClientModInitializer {
	override fun onInitializeClient() {
		RaidOddsPreview.register()
		ClientPlayNetworking.registerGlobalReceiver(RaidOddsPayload.TYPE) { payload, context ->
			context.client().setScreen(RaidOddsScreen(payload))
		}
		ClientPlayNetworking.registerGlobalReceiver(RaidAvailabilityPayload.TYPE) { payload, context ->
            (context.client().screen as? RaidMenuState)?.setOccupied(payload.occupied)
		}
	}
}
