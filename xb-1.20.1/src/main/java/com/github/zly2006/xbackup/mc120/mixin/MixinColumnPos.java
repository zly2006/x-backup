package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.multi.IColumnPos;
import net.minecraft.util.math.ColumnPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ColumnPos.class)
public class MixinColumnPos implements IColumnPos {
    @Shadow @Final public int x;

    @Shadow @Final public int z;

    @Override
    public int x$x_backup() {
        return x;
    }

    @Override
    public int z$x_backup() {
        return z;
    }
}
