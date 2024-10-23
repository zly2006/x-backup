package com.github.zly2006.xbackup.mc121.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import net.minecraft.server.world.ChunkTaskPrioritySystem;
import net.minecraft.server.world.LevelPrioritizedQueue;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.MessageListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.function.Function;

@Mixin(ChunkTaskPrioritySystem.class)
public class MixinChunkTaskPrioritySystem implements RestoreAware {
    @Shadow @Final private Map<MessageListener<?>, LevelPrioritizedQueue<? extends Function<MessageListener<Unit>, ?>>> queues;

    @Override
    public void preRestore() {
        queues.values().forEach(
                queue -> ((RestoreAware) queue).preRestore()
        );
    }

    @Override
    public void postRestore() {
        queues.values().forEach(
                queue -> ((RestoreAware) queue).postRestore()
        );
    }
}
