package com.github.zly2006.xbackup.mc121_2.mixin;

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

    @Shadow public boolean savingDisabled;

    @Override
    public void preRestore() {
        savingDisabled = false;
        chunkManager.removePersistentTickets();
        chunkManager.tick(() -> true, false);
        ((RestoreAware) this.entityManager).preRestore();
        ((RestoreAware) this.chunkManager.chunkLoadingManager).preRestore();
    }

    @Override
    public void postRestore() {
        ((RestoreAware) this.entityManager).postRestore();
        ((RestoreAware) this.chunkManager.chunkLoadingManager).postRestore();
    }
}
