package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.XBackup;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MixinServer {
    @Inject(
            method = "save",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disableSave(CallbackInfoReturnable<Boolean> cir) {
        if (XBackup.INSTANCE.getDisableSaving()) {
            cir.setReturnValue(true);
        }
    }
}
