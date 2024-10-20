package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerWorld.class)
public class MixinWorld implements RestoreAware {
    @Shadow @Final private ServerChunkManager chunkManager;

    @Shadow @Final private ServerEntityManager<Entity> entityManager;

    @Override
    public void preRestore() {
        ((RestoreAware) this.entityManager).preRestore();
        ((RestoreAware) this.chunkManager.threadedAnvilChunkStorage).preRestore();
    }

    @Override
    public void postRestore() {
        ((RestoreAware) this.entityManager).postRestore();
        ((RestoreAware) this.chunkManager.threadedAnvilChunkStorage).postRestore();
    }
}
