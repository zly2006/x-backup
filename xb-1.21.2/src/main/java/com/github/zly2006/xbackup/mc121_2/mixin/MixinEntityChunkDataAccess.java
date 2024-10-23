package com.github.zly2006.xbackup.mc121_2.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.storage.ChunkPosKeyedStorage;
import net.minecraft.world.storage.EntityChunkDataAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityChunkDataAccess.class)
public class MixinEntityChunkDataAccess implements RestoreAware {
    @Shadow @Final private LongSet emptyChunks;

    @Shadow @Final private ChunkPosKeyedStorage storage;

    @Override
    public void preRestore() {
        emptyChunks.clear();
        ((RestoreAware) storage.worker).preRestore();
    }

    @Override
    public void postRestore() {
        emptyChunks.clear();
        ((RestoreAware) storage.worker).postRestore();
    }
}
