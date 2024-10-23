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
public class MixinStorageIoWorker implements RestoreAware {
    @Shadow @Final private RegionBasedStorage storage;

    @Shadow @Final private Map<ChunkPos, Object> results;

    @Override
    public void preRestore() {
        results.clear();
        ((RestoreAware) (Object) this.storage).preRestore();
    }

    @Override
    public void postRestore() {
        results.clear();
        ((RestoreAware) (Object) this.storage).postRestore();
    }
}
