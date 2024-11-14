package com.github.zly2006.xbackup.mc121.mixin.dev;

import com.github.zly2006.xbackup.mc121.AKt;
import com.github.zly2006.xbackup.mc121.FakeChunk;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkHolder.class)
public abstract class GetEmptyChunk extends AbstractChunkHolder implements FakeChunk {
    @Shadow @Final private HeightLimitView world;
    @Shadow private volatile CompletableFuture<OptionalChunk<WorldChunk>> tickingFuture;
    @Shadow private CompletableFuture<?> savingFuture;
    @Shadow private volatile CompletableFuture<OptionalChunk<WorldChunk>> entityTickingFuture;
    @Shadow private volatile CompletableFuture<OptionalChunk<WorldChunk>> accessibleFuture;
    @Shadow private CompletableFuture<?> postProcessingFuture;
    @Shadow private boolean pendingBlockUpdates;
    private WorldChunk empty = null;

    public GetEmptyChunk(ChunkPos pos) {
        super(pos);
    }

    @Override
    public void fake() {
        if (world instanceof ServerWorld serverWorld) {
            if (empty == null) {
                empty = AKt.genEmptyChunk(this.pos, serverWorld);
                serverWorld.getChunkManager().chunkLoadingManager.sendToPlayers(empty);
            }
            tickingFuture = CompletableFuture.completedFuture(OptionalChunk.of(empty));
            savingFuture = CompletableFuture.completedFuture(OptionalChunk.of(empty));
            entityTickingFuture = CompletableFuture.completedFuture(OptionalChunk.of(empty));
            accessibleFuture = CompletableFuture.completedFuture(OptionalChunk.of(empty));
            postProcessingFuture = CompletableFuture.completedFuture(OptionalChunk.of(empty));
        }
    }

    @Inject(
            method = "flushUpdates",
            at = @At("HEAD")
    )
    private void flushUpdates(WorldChunk chunk, CallbackInfo ci) {
        if (empty != null && pendingBlockUpdates) {
            System.out.println("GetEmptyChunk: flushUpdates, pos=" + pos);
        }
    }

    @Override
    public CompletableFuture<OptionalChunk<Chunk>> load(ChunkStatus requestedStatus, ServerChunkLoadingManager chunkLoadingManager) {
        if (empty != null) {
            return CompletableFuture.completedFuture(OptionalChunk.of(empty));
        } else {
            return super.load(requestedStatus, chunkLoadingManager);
        }
    }
}
