package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.multi.MultiVersioned
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import java.util.*

@Suppress("NOTHING_TO_INLINE")
object Utils {
    val onedriveSupport = FabricLoader.getInstance().isModLoaded("x_backup_onedrive")

    val service: MultiVersioned by lazy {
        ServiceLoader.load(MultiVersioned::class.java).single()
    }

    inline fun ServerCommandSource.send(text: CrossVersionText) {
        service.sendMessage(this, text, false)
    }

    inline fun MinecraftServer.setAutoSaving(value: Boolean) {
        service.setAutoSaving(this, value)
    }

    inline fun MinecraftServer.save() {
        service.save(this)
    }

    inline fun MinecraftServer.finishRestore() {
        service.finishRestore(this)
    }

    inline fun MinecraftServer.broadcast(text: CrossVersionText) {
        service.broadcast(this, text)
    }
}
