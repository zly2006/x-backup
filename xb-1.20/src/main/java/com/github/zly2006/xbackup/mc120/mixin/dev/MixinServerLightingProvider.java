package com.github.zly2006.xbackup.mc120.mixin.dev;

import com.github.zly2006.xbackup.XBackup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLightingProvider.class)
public class MixinServerLightingProvider extends LightingProvider {

    public MixinServerLightingProvider(ChunkProvider chunkProvider, boolean hasBlockLight, boolean hasSkyLight) {
        super(chunkProvider, hasBlockLight, hasSkyLight);
    }

    /**
     * Lighting suppression? Just make an endless loop here
     */
    @Inject(method = "runTasks", at = @At(value = "HEAD"))
    private void onExecutingLightUpdates(CallbackInfo ci)
    {
//        while (FabricLoader.getInstance().isDevelopmentEnvironment() && XBackup.INSTANCE.getServerStarted())
//        {
//            Thread.yield();
//        }
    }
}
