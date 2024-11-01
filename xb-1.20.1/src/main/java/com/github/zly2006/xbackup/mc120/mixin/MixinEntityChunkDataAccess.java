package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.storage.EntityChunkDataAccess;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityChunkDataAccess.class)
public class MixinEntityChunkDataAccess implements RestoreAware {
    @Shadow @Final private LongSet emptyChunks;

    @Shadow @Final private StorageIoWorker dataLoadWorker;

    @Override
    public void preRestore() {
        emptyChunks.clear();
        ((RestoreAware) dataLoadWorker).preRestore();
    }

    @Override
    public void postRestore() {
        emptyChunks.clear();
        ((RestoreAware) dataLoadWorker).postRestore();
    }
}
