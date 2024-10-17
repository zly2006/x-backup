package com.github.zly2006.xbackup.mixin;

import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerChunkLoadingManager.class)
public interface IMixinChunkMap {
}
