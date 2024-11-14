package com.github.zly2006.xbackup.mc121

import net.minecraft.block.Blocks
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.chunk.ProtoChunk
import net.minecraft.world.chunk.UpgradeData
import net.minecraft.world.chunk.WorldChunk

fun genEmptyChunk(pos: ChunkPos, world: ServerWorld): WorldChunk {
    val proto = ProtoChunk(
        pos,
        UpgradeData.NO_UPGRADE_DATA,
        world,
        world.registryManager.get(RegistryKeys.BIOME),
        null
    )
    /**
     # # # #   # # # #
     #         #     #
     # # # #   # # # # #
           #   #       #
     # # # #   # # # # #
     */
    // # # # #   # # # #
    proto.setBlockState(BlockPos(0, 0, 0), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(1, 0, 0), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(2, 0, 0), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(3, 0, 0), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(5, 0, 0), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(6, 0, 0), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(7, 0, 0), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(8, 0, 0), Blocks.BEDROCK.defaultState, false)
    // #         #     #
    proto.setBlockState(BlockPos(0, 0, 1), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(5, 0, 1), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(8, 0, 1), Blocks.BEDROCK.defaultState, false)
    // # # # #   # # # # #
    proto.setBlockState(BlockPos(0, 0, 2), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(1, 0, 2), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(2, 0, 2), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(3, 0, 2), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(5, 0, 2), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(6, 0, 2), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(7, 0, 2), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(8, 0, 2), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(9, 0, 2), Blocks.BEDROCK.defaultState, false)
    //       #   #       #
    proto.setBlockState(BlockPos(3, 0, 3), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(5, 0, 3), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(9, 0, 3), Blocks.BEDROCK.defaultState, false)
    // # # # #   # # # # #
    proto.setBlockState(BlockPos(0, 0, 4), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(1, 0, 4), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(2, 0, 4), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(3, 0, 4), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(5, 0, 4), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(6, 0, 4), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(7, 0, 4), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(8, 0, 4), Blocks.BEDROCK.defaultState, false)
    proto.setBlockState(BlockPos(9, 0, 4), Blocks.BEDROCK.defaultState, false)
    return WorldChunk(world, proto) {
        // Do nothing
    }
}
