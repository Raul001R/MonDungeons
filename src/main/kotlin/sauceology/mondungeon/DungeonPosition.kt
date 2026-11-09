package sauceology.mondungeon

import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

data class DungeonPosition(val dimension: ResourceKey<Level>, val position: Vec3, val yaw: Float, val pitch: Float) {
    companion object {
        fun capture(player: ServerPlayer) = DungeonPosition(player.level().dimension(), player.position(), player.yRot, player.xRot)
    }
}
