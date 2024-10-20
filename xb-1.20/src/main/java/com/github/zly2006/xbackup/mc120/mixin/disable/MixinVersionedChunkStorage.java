package com.github.zly2006.xbackup.mc120.mixin.disable;

import com.github.zly2006.xbackup.XBackup;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VersionedChunkStorage.class)
public class MixinVersionedChunkStorage {
    @Inject(
            method = "setNbt",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelSave(CallbackInfo ci) {
        if (XBackup.INSTANCE.getDisableSaving()) {
            ci.cancel();
        }
    }
}
