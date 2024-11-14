package com.github.zly2006.xbackup.mc120

import com.github.zly2006.xbackup.CrossVersionText
import com.github.zly2006.xbackup.XBackup
import com.github.zly2006.xbackup.multi.MultiVersioned
import com.github.zly2006.xbackup.multi.RestoreAware
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import net.minecraft.world.dimension.DimensionType
import java.nio.file.Path

class Impl : MultiVersioned {
    override val implementationTitle: String
        get() = "XBackup 1.20.4"

    override fun parseText(text: CrossVersionText): MutableText {
        return when (text) {
            is CrossVersionText.CombinedText -> Text.empty().apply {
                text.texts.forEach { append(parseText(it)) }
            }
            is CrossVersionText.LiteralText -> Text.literal(text.text)
            is CrossVersionText.ClickableText -> {
                var base = parseText(text.text)
                base = base.styled {
                    it.withClickEvent(
                        ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            text.command
                        )
                    )
                }
                if (text.hover != null) {
                    base = base.styled {
                        it.withHoverEvent(
                            HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                parseText(text.hover!!)
                            )
                        )
                    }
                }
                base
            }
        }
    }

    override fun sendMessage(
        source: ServerCommandSource,
        text: CrossVersionText,
        broadcastToOps: Boolean,
    ) {
        source.sendFeedback({ parseText(text) }, broadcastToOps)
    }

    override fun broadcast(server: MinecraftServer, text: CrossVersionText) {
        server.playerManager.broadcast(parseText(text), false)
    }

    override fun save(server: MinecraftServer) {
        server.saveAll(false, false, true)
    }

    override fun setAutoSaving(server: MinecraftServer, autoSaving: Boolean) {
        server.worlds.forEach { it.savingDisabled = !autoSaving }
    }

    override fun prepareRestore(server: MinecraftServer, reason: String) {
        XBackup.reason = reason
        XBackup.blockPlayerJoin = true
        XBackup.disableWatchdog = true

        if (server.isSingleplayer || !server.isOnThread) {
            // client-server
            server.stop(true)
        }
        else {
            server.playerManager.playerList.toList().forEach {
                if (it.networkHandler.latency == 0) {
                    // fuck carpet
                    it.networkHandler.disconnect(Text.translatable("multiplayer.disconnect.duplicate_login"))
                }
                it.networkHandler.disconnect(Text.of(reason))
            }

            server.runTasks { true }
            while (server.playerManager.playerList.isNotEmpty()) {
                server.networkIo.tick()
                server.runTasks { true }
            }

            while (server.worlds.any { reasonOfDelayShutdown(it).isNotEmpty() })
            {
                for (serverWorldx in server.worlds) {
                    (serverWorldx as RestoreAware).preRestore()
                }

                 while (server.runTask()) {

                 }
            }
        }
    }

    override fun finishRestore(server: MinecraftServer) {
        XBackup.blockPlayerJoin = false
        XBackup.disableWatchdog = false
        XBackup.disableSaving = false

        server.worlds.forEach {
            (it as RestoreAware).postRestore()
        }
    }

    companion object {
        fun reasonOfDelayShutdown(world: ServerWorld): String {
            val lightingProvider = world.lightingProvider
            val chunksToUnload = world.chunkManager.threadedAnvilChunkStorage.unloadedChunks
            val currentChunkHolders = world.chunkManager.threadedAnvilChunkStorage.currentChunkHolders
            val pointOfInterestStorage = world.pointOfInterestStorage
            val unloadedChunks = world.chunkManager.threadedAnvilChunkStorage.unloadedChunks
            val unloadTaskQueue = world.chunkManager.threadedAnvilChunkStorage.unloadTaskQueue
            val chunkTaskPrioritySystem = world.chunkManager.threadedAnvilChunkStorage.chunkTaskPrioritySystem
            val ticketManager = world.chunkManager.ticketManager

            return buildString {
//                if (lightingProvider.hasUpdates()) {
//                    append("lightingProvider.hasUpdates() ")
//                }
                if (chunksToUnload.isNotEmpty()) {
                    append("chunksToUnload.isNotEmpty() ")
                }
                if (currentChunkHolders.isNotEmpty()) {
                    append("currentChunkHolders.isNotEmpty() ")
                }
                if (pointOfInterestStorage.hasUnsavedElements()) {
                    append("pointOfInterestStorage.hasUnsavedElements() ")
                }
                if (unloadedChunks.isNotEmpty()) {
                    append("unloadedChunks.isNotEmpty() ")
                }
                if (unloadTaskQueue.isNotEmpty()) {
                    append("unloadTaskQueue.isNotEmpty() ")
                }
                if (chunkTaskPrioritySystem.shouldDelayShutdown()) {
                    append("chunkTaskPrioritySystem.shouldDelayShutdown() ")
                }
                if (ticketManager.shouldDelayShutdown()) {
                    append("ticketManager.shouldDelayShutdown() ")
                }
            }
        }
    }

    override fun isFileInWorld(world: ServerWorld, p: Path): Boolean {
        val path = DimensionType.getSaveDirectory(
            world.registryKey,
            world.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
        ).normalize()
        return p.normalize().startsWith(path)
    }
}
