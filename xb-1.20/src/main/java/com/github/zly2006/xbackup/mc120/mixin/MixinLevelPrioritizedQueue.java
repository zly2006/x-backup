package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.LevelPrioritizedQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelPrioritizedQueue.class)
public class MixinLevelPrioritizedQueue implements RestoreAware {
    @Shadow @Final private LongSet blockingChunks;

    @Override
    public void preRestore() {
        blockingChunks.clear();
    }

    @Override
    public void postRestore() {

    }
}
