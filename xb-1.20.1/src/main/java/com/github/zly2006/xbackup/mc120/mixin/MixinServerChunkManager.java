package com.github.zly2006.xbackup.mc120.mixin;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.util.thread.ThreadExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BooleanSupplier;

@Mixin(ServerChunkManager.class)
public class MixinServerChunkManager {
    @Redirect(
            method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkManager$MainThreadExecutor;runTasks(Ljava/util/function/BooleanSupplier;)V"
            )
    )
    private void runTasks(ServerChunkManager.MainThreadExecutor instance, BooleanSupplier isDone) {
        long time = System.currentTimeMillis();
        instance.runTasks(
                () -> isDone.getAsBoolean() || System.currentTimeMillis() - time > 1000
        );
        if (!isDone.getAsBoolean()) {
            throw new RuntimeException("Chunk load timeout");
        }
    }
}
