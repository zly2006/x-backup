package com.github.zly2006.xbackup.mc120.mixin;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerChunkManager.class)
public class MixinServerChunkManager {
    @Shadow @Final private ServerWorld world;

}
