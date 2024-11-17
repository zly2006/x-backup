package me.lucko.luckperms.common.plugin;

import me.lucko.luckperms.common.plugin.scheduler.SchedulerAdapter;

@SuppressWarnings("unused")
public class AbstractLuckPermsPlugin {
    @SuppressWarnings("DataFlowIssue")
    public final void disable() {
        SchedulerAdapter schedulerAdapter = null;
        schedulerAdapter.shutdownExecutor();
        schedulerAdapter.shutdownScheduler();
    }
}
