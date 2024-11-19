package com.github.zly2006.xbackup.mixin.compat;

import com.github.zly2006.xbackup.XBackup;
import me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin;
import me.lucko.luckperms.common.plugin.scheduler.SchedulerAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AbstractLuckPermsPlugin.class, remap = false)
public class MixinLuckPermsPlugin {
    @Redirect(
            method = "disable",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lme/lucko/luckperms/common/plugin/scheduler/SchedulerAdapter;shutdownExecutor()V"
            ),
            require = 0
    )
    private void shutdownExecutor(SchedulerAdapter schedulerAdapter) {
        if (!XBackup.INSTANCE.getRestoring()) {
            schedulerAdapter.shutdownExecutor();
        }
    }

    @Redirect(
            method = "disable",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lme/lucko/luckperms/common/plugin/scheduler/SchedulerAdapter;shutdownScheduler()V"
            ),
            require = 0
    )
    private void shutdownScheduler(SchedulerAdapter schedulerAdapter) {
        if (!XBackup.INSTANCE.getRestoring()) {
            schedulerAdapter.shutdownScheduler();
        }
    }
}