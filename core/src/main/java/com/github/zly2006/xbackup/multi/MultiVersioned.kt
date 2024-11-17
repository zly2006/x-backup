package com.github.zly2006.xbackup.multi

import com.github.zly2006.xbackup.CrossVersionText
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import java.nio.file.Path

interface MultiVersioned {
    val implementationTitle: String

    fun parseText(text: CrossVersionText): Text

    fun sendMessage(source: ServerCommandSource, text: CrossVersionText, broadcastToOps: Boolean)

    @Deprecated("Unused")
    fun save(server: MinecraftServer)

    fun setAutoSaving(server: MinecraftServer, autoSaving: Boolean)

    @Deprecated("Old method")
    fun prepareRestore(server: MinecraftServer, reason: String)

    fun finishRestore(server: MinecraftServer)

    fun isFileInWorld(world: ServerWorld, p: Path): Boolean

    fun broadcast(server: MinecraftServer, text: CrossVersionText)

    fun kickAllPlayers(minecraftServer: MinecraftServer, reason: CrossVersionText)
}
