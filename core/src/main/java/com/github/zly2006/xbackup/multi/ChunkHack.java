package com.github.zly2006.xbackup.multi;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public interface ChunkHack {
    void replaceChunk(ChunkPos pos, WorldChunk chunk);
}
