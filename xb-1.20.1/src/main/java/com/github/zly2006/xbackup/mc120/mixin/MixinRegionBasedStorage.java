package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.XBackup;
import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

@Mixin(RegionBasedStorage.class)
public class MixinRegionBasedStorage implements RestoreAware {
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<RegionFile> cachedRegionFiles;

    @Shadow @Final private Path directory;

    @Override
    public void preRestore() {
        cachedRegionFiles.forEach((k, v) -> {
            try {
                v.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        cachedRegionFiles.clear();
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
        XBackup.INSTANCE.getLog().info("[X Backup] postRestore, " + cachedRegionFiles.size() + " region files closed of " + this.directory);
        cachedRegionFiles.clear();
    }
}
