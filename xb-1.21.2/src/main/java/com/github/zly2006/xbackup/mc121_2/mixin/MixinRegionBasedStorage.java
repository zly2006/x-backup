package com.github.zly2006.xbackup.mc121_2.mixin;

import com.github.zly2006.xbackup.XBackup;
import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;

@Mixin(RegionBasedStorage.class)
public class MixinRegionBasedStorage implements RestoreAware {
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<RegionFile> cachedRegionFiles;

    @Shadow @Final private Path directory;

    private boolean restoring = false;

    @Override
    public void preRestore() {
        cachedRegionFiles.forEach((k, v) -> {
            try {
                v.close();
            } catch (ClosedChannelException ignored) {

            } catch (Exception e) {
                XBackup.INSTANCE.getLog().error("[X Backup] Error closing region file " + this.directory + "/" + new ChunkPos(k), e);
            }
        });
        cachedRegionFiles.clear();
        restoring = true;
    }

    @Inject(
            method = "write",
            at = @At(value = "INVOKE", target = "Ljava/io/DataOutputStream;close()V", ordinal = 0),
            cancellable = true
    )
    private void getRegionFile(ChunkPos pos, NbtCompound nbt, CallbackInfo ci) {
        if (restoring) {
            ci.cancel();
        }
    }

    @Override
    public void postRestore() {
        cachedRegionFiles.forEach((k, v) -> {
            try {
                v.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        cachedRegionFiles.clear();
        restoring = false;
    }
}
