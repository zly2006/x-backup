package com.github.zly2006.xbackup.mc121_2.mixin;

import com.github.zly2006.xbackup.XBackup;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinSPNH {
    @Shadow public ServerPlayerEntity player;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void onInit(CallbackInfo ci) {
        if (XBackup.INSTANCE.getBlockPlayerJoin()) {
            player.networkHandler.disconnect(Text.of(XBackup.INSTANCE.getReason()));
        }
    }
}
