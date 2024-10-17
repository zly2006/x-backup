package com.github.zly2006.xbackup.mc121.mixin;

import com.github.zly2006.xbackup.access.ServerAccess;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MinecraftServer.class)
public class MixinServer implements ServerAccess {
    @Override
    public void restoreStart() {

    }

    @Override
    public void restoreStop() {

    }

    @Override
    public void saveWorld() {

    }
}
