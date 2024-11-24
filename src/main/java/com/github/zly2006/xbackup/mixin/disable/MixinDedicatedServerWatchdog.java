package com.github.zly2006.xbackup.mixin.disable;

import com.github.zly2006.xbackup.XBackup;
import net.minecraft.server.dedicated.DedicatedServerWatchdog;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DedicatedServerWatchdog.class)
public class MixinDedicatedServerWatchdog {
    @Shadow @Final private long maxTickTime;

    @Redirect(
            method = "run",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/dedicated/DedicatedServerWatchdog;maxTickTime:J",
                    ordinal = 0
            )
    )
    private long redirectMaxTickTime(DedicatedServerWatchdog instance) {
        if (XBackup.INSTANCE.getDisableWatchdog()) {
            return Long.MAX_VALUE;
        } else return maxTickTime;
    }
}
