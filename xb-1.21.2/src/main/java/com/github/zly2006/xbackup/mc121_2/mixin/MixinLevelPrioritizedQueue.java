package com.github.zly2006.xbackup.mc121_2.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.world.LevelPrioritizedQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(LevelPrioritizedQueue.class)
public class MixinLevelPrioritizedQueue implements RestoreAware {
    @Shadow @Final public static int LEVEL_COUNT;

    @Shadow @Final private String name;

    @Shadow private volatile int topPriority;

    @Shadow @Final private List<Long2ObjectLinkedOpenHashMap<List<Runnable>>> values;

    @Override
    public void preRestore() {
        if ("light_queue".equals(name)) {
            values.forEach(Long2ObjectLinkedOpenHashMap::clear);
            this.topPriority = LEVEL_COUNT;
        }
    }

    @Override
    public void postRestore() {
    }
}
