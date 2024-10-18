package com.github.zly2006.xbackup.multi

import com.github.zly2006.xbackup.CrossVersionText
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

interface MultiVersioned {
    val implementationTitle: String

    fun parseText(text: CrossVersionText): Text

    fun sendMessage(source: ServerCommandSource, text: CrossVersionText, broadcastToOps: Boolean)

    fun save(server: MinecraftServer)

    fun setAutoSaving(server: MinecraftServer, autoSaving: Boolean)

    fun prepareRestore(server: MinecraftServer, reason: String)

    fun finishRestore(server: MinecraftServer)
}
