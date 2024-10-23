package com.github.zly2006.xbackup.mc121.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.*;
import net.minecraft.world.storage.ChunkDataAccess;
import net.minecraft.world.storage.ChunkDataList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;

@Mixin(ServerEntityManager.class)
public abstract class MixinServerEntityManager implements RestoreAware {
    @Shadow @Final private LongSet pendingUnloads;

    @Shadow @Final private ChunkDataAccess<?> dataAccess;

    @Shadow @Final private Long2ObjectMap<?> managedStatuses;

    @Shadow @Final private Long2ObjectMap<EntityTrackingStatus> trackingStatuses;

    @Shadow @Final Set<UUID> entityUuids;

    @Mutable @Shadow @Final SectionedEntityCache<Entity> cache;

    @Mutable
    @Shadow @Final private EntityIndex<Entity> index;

    @Shadow protected abstract LongSet getLoadedChunks();

    @Shadow protected abstract boolean unload(long chunkPos);

    @Mutable
    @Shadow @Final private EntityLookup<Entity> lookup;

    @Shadow @Final private Queue<ChunkDataList<?>> loadingQueue;

    @Override
    public void preRestore() {
        getLoadedChunks().forEach(this::unload);
        this.trackingStatuses.clear();
        this.managedStatuses.clear();
        this.pendingUnloads.clear();
        this.loadingQueue.clear();
        ((RestoreAware) dataAccess).preRestore();
    }

    @Override
    public void postRestore() {
        this.entityUuids.clear();
        this.pendingUnloads.clear();
        this.loadingQueue.clear();
        this.managedStatuses.clear();
        this.trackingStatuses.clear();
        this.cache = new SectionedEntityCache<>(Entity.class, this.trackingStatuses);
        this.index = new EntityIndex<>();
        this.lookup = new SimpleEntityLookup<>(this.index, this.cache);
        ((RestoreAware) dataAccess).postRestore();
    }
}
