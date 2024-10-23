package com.github.zly2006.xbackup.mc121_2.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RegionBasedStorage.class)
public class MixinRegionBasedStorage implements RestoreAware {
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<RegionFile> cachedRegionFiles;

    @Override
    public void preRestore() {
        cachedRegionFiles.clear();
    }

    @Override
    public void postRestore() {
        cachedRegionFiles.clear();
    }
}
