package com.github.zly2006.xbackup.mixin.disable;

import com.github.zly2006.xbackup.XBackup;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StorageIoWorker.class)
public abstract class MixinStorageIoWorker {
    @Inject(
            method = "writeResult",
            at = @At("HEAD"),
            cancellable = true
    )
    private void writeResult(CallbackInfo ci) {
        if (XBackup.INSTANCE.getDisableSaving()) {
            ci.cancel();
        }
    }
}
