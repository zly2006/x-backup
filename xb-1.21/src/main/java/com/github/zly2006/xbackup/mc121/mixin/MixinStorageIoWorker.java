package com.github.zly2006.xbackup.mc121.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(StorageIoWorker.class)
public abstract class MixinStorageIoWorker implements RestoreAware {
    @Shadow @Final private RegionBasedStorage storage;

    @Shadow @Final private Map<ChunkPos, StorageIoWorker.Result> results;

    @Shadow protected abstract void write(ChunkPos pos, StorageIoWorker.Result result);

    @Override
    public void preRestore() {
//        while (!this.results.isEmpty()) {
//            Iterator<Map.Entry<ChunkPos, StorageIoWorker.Result>> iterator = this.results.entrySet().iterator();
//            Map.Entry<ChunkPos, StorageIoWorker.Result> entry = iterator.next();
//            iterator.remove();
//            this.write(entry.getKey(), entry.getValue());
//        }
        results.clear();
        ((RestoreAware) (Object) this.storage).preRestore();
    }

    @Override
    public void postRestore() {
        ((RestoreAware) (Object) this.storage).postRestore();
    }
}
