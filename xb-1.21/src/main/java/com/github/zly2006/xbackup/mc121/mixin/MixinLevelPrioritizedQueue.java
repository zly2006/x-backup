package com.github.zly2006.xbackup.mc121.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.world.LevelPrioritizedQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Optional;

@Mixin(LevelPrioritizedQueue.class)
public class MixinLevelPrioritizedQueue implements RestoreAware {
    @Shadow @Final private List<Long2ObjectLinkedOpenHashMap<List<Optional<?>>>> levelToPosToElements;

    @Shadow @Final public static int LEVEL_COUNT;

    @Shadow private volatile int firstNonEmptyLevel;

    @Override
    public void preRestore() {
        levelToPosToElements.forEach(Long2ObjectLinkedOpenHashMap::clear);
        this.firstNonEmptyLevel = LEVEL_COUNT;
    }

    @Override
    public void postRestore() {
    }
}
