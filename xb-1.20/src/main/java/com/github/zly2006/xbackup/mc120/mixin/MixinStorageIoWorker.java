package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.TaskExecutor;
import net.minecraft.util.thread.TaskQueue;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mixin(StorageIoWorker.class)
public abstract class MixinStorageIoWorker implements RestoreAware {
    @Shadow @Final private RegionBasedStorage storage;

    @Shadow @Final private Map<ChunkPos, StorageIoWorker.Result> results;

    @Shadow @Final private TaskExecutor<TaskQueue.PrioritizedTask> executor;

    @Shadow protected abstract void write(ChunkPos pos, StorageIoWorker.Result result);

    @Shadow protected abstract void writeRemainingResults();

    @Unique
    private boolean restoring = false;

    @Inject(
            method = "readChunkData",
            at = @At("HEAD"),
            cancellable = true
    )
    private void readChunkData(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<Optional<NbtCompound>>> cir) {
        if (restoring) {
            cir.setReturnValue(new CompletableFuture<>());
        }
    }

    @Override
    public void preRestore() {
        if (this.storage == null) return;
        restoring = true;
        while (executor != null && executor.getQueueSize() > 0) {
            executor.queue.poll();
        }
        this.results.clear();
        ((RestoreAware) (Object) this.storage).preRestore();
    }

    @Override
    public void postRestore() {
        if (this.storage == null) return;
        while (executor != null && executor.getQueueSize() > 0) {
            executor.queue.poll();
        }
        ((RestoreAware) (Object) this.storage).postRestore();
        restoring = false;
    }

    @Inject(
            method = "run",
            at = @At("HEAD"),
            cancellable = true
    )
    private void run(Supplier<Either<Object, Exception>> task, CallbackInfoReturnable<CompletableFuture<Object>> cir) {
        if (restoring) {
            cir.setReturnValue(new CompletableFuture<>());
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    private void writeResult() {
        try {
            if (!this.results.isEmpty()) {
                Iterator<Map.Entry<ChunkPos, StorageIoWorker.Result>> iterator = this.results.entrySet().iterator();
                Map.Entry<ChunkPos, StorageIoWorker.Result> entry = iterator.next();
                iterator.remove();
                this.write(entry.getKey(), entry.getValue());
                this.writeRemainingResults();
            }
        } catch (Throwable e) {
            if (restoring) {
                // Ignore exceptions during restore
                if (e instanceof ClosedChannelException || e.getCause() instanceof ClosedChannelException) {
                    return;
                }
                if (e instanceof ConcurrentModificationException) {
                    return;
                }
                if (e instanceof NoSuchElementException) {
                    return;
                }
            }
            throw e;
        }
    }
}
