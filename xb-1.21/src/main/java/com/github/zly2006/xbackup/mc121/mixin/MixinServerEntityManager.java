package com.github.zly2006.xbackup.mc121.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityTrackingStatus;
import net.minecraft.world.storage.ChunkDataAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerEntityManager.class)
public abstract class MixinServerEntityManager implements RestoreAware {
    @Shadow public abstract void flush();

    @Shadow @Final private LongSet pendingUnloads;

    @Shadow @Final private ChunkDataAccess<?> dataAccess;

    @Shadow @Final private Long2ObjectMap<?> managedStatuses;

    @Shadow @Final private Long2ObjectMap<EntityTrackingStatus> trackingStatuses;

    @Override
    public void preRestore() {
        flush();
        pendingUnloads.clear();
        managedStatuses.clear();
        trackingStatuses.clear();
        ((RestoreAware) dataAccess).preRestore();
    }

    @Override
    public void postRestore() {
        ((RestoreAware) dataAccess).postRestore();
    }
}
