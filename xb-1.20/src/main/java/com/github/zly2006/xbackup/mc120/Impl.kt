package com.github.zly2006.xbackup.mc120

import com.github.zly2006.xbackup.CrossVersionText
import com.github.zly2006.xbackup.XBackup
import com.github.zly2006.xbackup.multi.MultiVersioned
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text

class Impl : MultiVersioned {
    override val implementationTitle: String
        get() = "XBackup 1.20"

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
        XBackup.disableWatchdog = true
        server.playerManager.playerList.toList().forEach {
            it.networkHandler.disconnect(Text.of(reason))
        }

        if (server.isSingleplayer || !server.isOnThread) {
            // client-server
            server.stop(true)
        }
        else {
            server.runTasks { true }
            while (server.playerManager.playerList.isNotEmpty()) {
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
    }

    override fun finishRestore(server: MinecraftServer) {
        XBackup.blockPlayerJoin = false
        XBackup.disableWatchdog = false
    }
}
