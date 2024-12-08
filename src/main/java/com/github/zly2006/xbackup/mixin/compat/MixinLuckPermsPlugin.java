package com.github.zly2006.xbackup.mixin.compat;

import com.github.zly2006.xbackup.XBackup;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin;
import me.lucko.luckperms.common.plugin.scheduler.SchedulerAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AbstractLuckPermsPlugin.class, remap = false)
public class MixinLuckPermsPlugin {
    @WrapWithCondition(
            method = "disable",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lme/lucko/luckperms/common/plugin/scheduler/SchedulerAdapter;shutdownExecutor()V"
            ),
            require = 0
    )
    private boolean shutdownExecutor(SchedulerAdapter schedulerAdapter) {
        return !XBackup.INSTANCE.isRestoring();
    }

    @WrapWithCondition(
            method = "disable",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lme/lucko/luckperms/common/plugin/scheduler/SchedulerAdapter;shutdownScheduler()V"
            ),
            require = 0
    )
    private boolean shutdownScheduler(SchedulerAdapter schedulerAdapter) {
        return !XBackup.INSTANCE.isRestoring();
    }
}
