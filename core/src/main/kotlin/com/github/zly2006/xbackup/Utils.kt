package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.multi.MultiVersioned
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.util.*

object Utils {
    private val service by lazy {
        ServiceLoader.load(MultiVersioned::class.java).single()
    }

    fun ServerCommandSource.send(text: Text) {
        service.sendMessage(this, text, false)
    }

    fun MinecraftServer.setAutoSaving(value: Boolean) {
        service.setAutoSaving(this, value)
    }

    fun MinecraftServer.save() {
        service.save(this)
    }

    fun MinecraftServer.prepareRestore(reason: String) {
        service.prepareRestore(this, reason)
    }

    fun MinecraftServer.finishRestore() {
        service.finishRestore(this)
    }
}
