package com.github.zly2006.xbackup.mc120

import com.github.zly2006.xbackup.XBackup
import com.github.zly2006.xbackup.multi.MultiVersioned
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class Impl : MultiVersioned {
    override val implementationTitle: String
        get() = "XBackup 1.20"

    override fun sendMessage(
        source: ServerCommandSource,
        text: Text,
        broadcastToOps: Boolean,
    ) {
        source.sendFeedback({ text }, broadcastToOps)
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
        server.playerManager.playerList.toList().forEach {
            it.networkHandler.disconnect(Text.of(reason))
        }
        server.runTasks { true }
        while (!server.playerManager.playerList.isEmpty()) {
            server.networkIo.tick()
            server.runTasks { true }
        }

        for (world in server.worlds) {
            world.chunkManager.ticketManager.ticketsByPosition.forEach { p, l ->
                l.forEach {
                    world.chunkManager.ticketManager.removeTicket(p, it)
                }
            }
            world.chunkManager.updateChunks()
            world.chunkManager.threadedAnvilChunkStorage.unloadedChunks.addAll(
                world.chunkManager.threadedAnvilChunkStorage.loadedChunks
            )
            world.chunkManager.threadedAnvilChunkStorage.unloadChunks { true }
            world.chunkManager.threadedAnvilChunkStorage.currentChunkHolders.clear()
            world.chunkManager.threadedAnvilChunkStorage.updateHolderMap()
        }
    }

    override fun finishRestore(server: MinecraftServer) {
        XBackup.blockPlayerJoin = false
        XBackup.disableWatchdog = false

        if (server.hostProfile != null) {
            // client
            server.stop(false)
        }
    }
}
