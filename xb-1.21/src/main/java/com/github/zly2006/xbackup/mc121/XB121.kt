package com.github.zly2006.xbackup.mc121

import com.github.zly2006.xbackup.Commands
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

object XB121: ModInitializer {
    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
//            dispatcher.register {
//                literal("fake-chunk") {
//                    executes {
//                        val blockPos = it.source.player!!.blockPos
//                        it.source.world.chunkManager.initChunkCaches()
//                        val holder = it.source.world.chunkManager.getChunkHolder(ChunkPos(blockPos).toLong())
//                        (holder as FakeChunk).fake()
//                        1
//                    }
//                }
//            }
            Commands.register(dispatcher)
        }
    }
}
