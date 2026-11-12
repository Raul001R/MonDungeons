package sauceology.mondungeon.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import sauceology.mondungeon.RaidOddsPayload
import sauceology.mondungeon.RaidAvailabilityPayload
import sauceology.mondungeon.RaidPayoutPayload

object MonDungeonsClient : ClientModInitializer {
	override fun onInitializeClient() {
		DungeonNpcTextures.register()
		RaidOddsPreview.register()
		ClientPlayNetworking.registerGlobalReceiver(RaidPayoutPayload.TYPE) { payload, context ->
            context.client().setScreen(RaidSpinScreen(payload))
        }
		ClientPlayNetworking.registerGlobalReceiver(RaidOddsPayload.TYPE) { payload, context ->
			context.client().setScreen(RaidOddsScreen(payload))
		}
		ClientPlayNetworking.registerGlobalReceiver(RaidAvailabilityPayload.TYPE) { payload, context ->
            (context.client().screen as? RaidMenuState)?.setOccupied(payload.occupied)
		}
	}
}
