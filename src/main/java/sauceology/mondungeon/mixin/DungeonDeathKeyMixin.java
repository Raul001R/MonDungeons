package sauceology.mondungeon.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import sauceology.mondungeon.DungeonDeathKeys;
import sauceology.mondungeon.ProtectedDungeonKey;

@Mixin(Inventory.class)
public abstract class DungeonDeathKeyMixin {
    @WrapMethod(method = "dropAll")
    private void mondungeons$retainRunKey(Operation<Void> original) {
        Inventory inventory = (Inventory) (Object) this;
        ProtectedDungeonKey key = DungeonDeathKeys.beforeDrop(inventory);
        try { original.call(); }
        finally { DungeonDeathKeys.afterDrop(inventory, key); }
    }
}
