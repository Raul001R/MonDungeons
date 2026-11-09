package sauceology.mondungeon.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sauceology.mondungeon.DungeonDeathKeys;
import sauceology.mondungeon.DungeonPayoutReceipt;

@Mixin(ServerPlayer.class)
public abstract class DungeonPlayerRecoveryMixin implements DungeonPayoutReceipt {
    @Unique private String mondungeons$receipt = "";

    @Override public String mondungeonsGetReceipt() { return mondungeons$receipt; }
    @Override public void mondungeonsSetReceipt(String receipt) { mondungeons$receipt = receipt; }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void mondungeons$readReceipt(CompoundTag tag, CallbackInfo callback) {
        mondungeons$receipt = tag.getString("MonDungeonsPayoutReceipt");
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void mondungeons$writeReceipt(CompoundTag tag, CallbackInfo callback) {
        if (!mondungeons$receipt.isEmpty()) tag.putString("MonDungeonsPayoutReceipt", mondungeons$receipt);
    }

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void mondungeons$restoreKey(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo callback) {
        mondungeons$receipt = ((DungeonPayoutReceipt) oldPlayer).mondungeonsGetReceipt();
        DungeonDeathKeys.transfer(oldPlayer, (ServerPlayer) (Object) this);
    }
}
