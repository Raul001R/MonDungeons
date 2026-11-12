package sauceology.mondungeon.client.mixin;

import com.cobblemon.mod.common.client.render.npc.NPCRenderer;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sauceology.mondungeon.client.DungeonNpcTextures;

@Mixin(value = NPCRenderer.class, remap = false)
public class DungeonNpcRendererMixin {
    // Cobblemon's concrete overload retains this name in its production JAR; its bridge is mapped separately.
    @Inject(target = @Desc(value = "getTextureLocation", args = NPCEntity.class, ret = ResourceLocation.class),
        at = @At("RETURN"), cancellable = true, remap = false)
    private void mondungeons$skinFallback(NPCEntity npc, CallbackInfoReturnable<ResourceLocation> callback) {
        callback.setReturnValue(DungeonNpcTextures.resolve(npc, callback.getReturnValue()));
    }
}
