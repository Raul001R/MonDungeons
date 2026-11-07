package sauceology.mondungeon.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sauceology.mondungeon.DungeonKeyDiscovery;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class DungeonKeyDiscoveryMixin {
    @Inject(method = "openMenu", at = @At("RETURN"))
    private void mondungeons$announceKey(MenuProvider provider, CallbackInfoReturnable<OptionalInt> callback) {
        if (callback.getReturnValue().isPresent()) {
            DungeonKeyDiscovery.onChestOpened((ServerPlayer) (Object) this);
        }
    }
}
