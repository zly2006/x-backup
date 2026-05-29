package com.github.zly2006.xbackup.mixin.disable;

import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.*;

@Mixin(RegionFileStorage.class)
public abstract class MixinStorageIoWorker {

}
