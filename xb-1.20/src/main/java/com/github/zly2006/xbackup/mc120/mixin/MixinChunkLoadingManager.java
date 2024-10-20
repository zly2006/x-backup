package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinChunkLoadingManager extends VersionedChunkStorage implements RestoreAware  {
    @Shadow @Final private ServerWorld world;

    @Shadow @Final private Int2ObjectMap<ThreadedAnvilChunkStorage.EntityTracker> entityTrackers;

    @Shadow protected abstract void unloadEntity(Entity entity);

    public MixinChunkLoadingManager(Path directory, DataFixer dataFixer, boolean dsync) {
        super(directory, dataFixer, dsync);
    }

    @Override
    public void preRestore() {
        ((RestoreAware) this.getWorker()).preRestore();
    }

    @Override
    public void postRestore() {
        ((RestoreAware) this.getWorker()).postRestore();
    }
}
