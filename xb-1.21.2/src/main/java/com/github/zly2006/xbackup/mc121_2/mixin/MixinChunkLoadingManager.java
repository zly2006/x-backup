package com.github.zly2006.xbackup.mc121_2.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import com.mojang.datafixers.DataFixer;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.world.chunk.ChunkLoader;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;
import java.util.List;

@Mixin(ServerChunkLoadingManager.class)
public abstract class MixinChunkLoadingManager extends VersionedChunkStorage implements RestoreAware  {
    @Shadow @Final private ServerChunkLoadingManager.TicketManager ticketManager;

    @Shadow @Final private List<ChunkLoader> loaders;

    public MixinChunkLoadingManager(StorageKey storageKey, Path directory, DataFixer dataFixer, boolean dsync) {
        super(storageKey, directory, dataFixer, dsync);
    }

    @Override
    public void preRestore() {
        ((RestoreAware) this.ticketManager).preRestore();
//        ((RestoreAware) this.chunkTaskPrioritySystem).preRestore();
        ((RestoreAware) this.getWorker()).preRestore();
        // 1.21 only
        for (ChunkLoader loader : loaders) {
            loader.markPendingDisposal();
            loader.run();
        }
    }

    @Override
    public void postRestore() {
        ((RestoreAware) this.ticketManager).postRestore();
//        ((RestoreAware) this.chunkTaskPrioritySystem).postRestore();
        ((RestoreAware) this.getWorker()).postRestore();
    }
}
