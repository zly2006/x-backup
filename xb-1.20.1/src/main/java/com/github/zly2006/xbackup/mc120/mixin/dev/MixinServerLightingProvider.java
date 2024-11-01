package com.github.zly2006.xbackup.mc120.mixin.dev;

import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLightingProvider.class)
public class MixinServerLightingProvider extends LightingProvider {
    public MixinServerLightingProvider(ChunkProvider chunkProvider, boolean hasBlockLight, boolean hasSkyLight) {
        super(chunkProvider, hasBlockLight, hasSkyLight);
    }
}
