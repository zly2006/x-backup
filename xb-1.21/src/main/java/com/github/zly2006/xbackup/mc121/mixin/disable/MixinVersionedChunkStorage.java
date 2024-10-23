package com.github.zly2006.xbackup.mc121.mixin.disable;

import com.github.zly2006.xbackup.XBackup;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(VersionedChunkStorage.class)
public class MixinVersionedChunkStorage {
    @Inject(
            method = "setNbt",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelSave(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (XBackup.INSTANCE.getDisableSaving()) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }
}
