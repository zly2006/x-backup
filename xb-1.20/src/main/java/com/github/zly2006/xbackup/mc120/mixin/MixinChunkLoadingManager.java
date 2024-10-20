package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import com.mojang.datafixers.DataFixer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

@Mixin(ThreadedAnvilChunkStorage.class)
public class MixinChunkLoadingManager extends VersionedChunkStorage implements RestoreAware  {
    @Shadow @Final private ServerWorld world;

    public MixinChunkLoadingManager(Path directory, DataFixer dataFixer, boolean dsync) {
        super(directory, dataFixer, dsync);
    }

    @Override
    public void preRestore() {
        ((RestoreAware) this.getWorker()).preRestore();
    }

    @Override
    public void postRestore() {
        ((RestoreAware) this.getWorker()).postRestore();
    }
}
