package com.github.zly2006.xbackup.mixin.disable;

import com.github.zly2006.xbackup.XBackup;
import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerWatchdog.class)
public class MixinDedicatedServerWatchdog {
    @Shadow @Final private long maxTickTimeNanos;

    @Redirect(
            method = "run",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/dedicated/ServerWatchdog;maxTickTimeNanos:J",
                    ordinal = 0
            )
    )
    private long redirectMaxTickTime(ServerWatchdog instance) {
        if (XBackup.INSTANCE.getDisableWatchdog()) {
            return Long.MAX_VALUE;
        } else return maxTickTimeNanos;
    }
}
