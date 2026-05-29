package com.github.zly2006.xbackup.mixin;

import com.github.zly2006.xbackup.XBackup;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftServer.class, priority = 1001)
public class MixinServer {
    @Inject(
            method = "saveAllChunks",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disableSave(CallbackInfoReturnable<Boolean> cir) {
        if (XBackup.INSTANCE.getDisableSaving()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "saveEverything",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disableSaveAll(CallbackInfoReturnable<Boolean> cir) {
        if (XBackup.INSTANCE.getDisableSaving()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "stopServer",
            at = @At("TAIL")
    )
    private void onShutdown(CallbackInfo ci) {
        XBackup.INSTANCE.getServerStopHook().invoke((MinecraftServer) (Object) this);
    }
}
