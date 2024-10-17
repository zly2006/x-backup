package com.github.zly2006.xbackup.multi

import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

interface MultiVersioned {
    val implementationTitle: String

    fun sendMessage(source: ServerCommandSource, text: Text, broadcastToOps: Boolean)

    fun save(server: MinecraftServer)

    fun setAutoSaving(server: MinecraftServer, autoSaving: Boolean)

    fun prepareRestore(server: MinecraftServer, reason: String)

    fun finishRestore(server: MinecraftServer)
}
