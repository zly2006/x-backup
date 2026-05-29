package com.github.zly2006.xbackup

import net.minecraft.server.MinecraftServer
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Component
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.dimension.DimensionType
import java.nio.file.Path

@Suppress("NOTHING_TO_INLINE")
object Utils {
    inline fun translate(key: String, vararg args: Any): MutableComponent {
        return Component.translatableWithFallback(
            key,
            I18n[key],
            *args
        )
    }

    inline fun CommandSourceStack.send(text: Component) {
        sendSystemMessage(text)
    }

    inline fun MinecraftServer.setAutoSaving(value: Boolean) {
        allLevels.forEach { it.noSave = !value }
    }

    inline fun MinecraftServer.save() {
        saveEverything(false, false, true)
        forceSynchronousWrites()
    }

    inline fun MinecraftServer.finishRestore() {
        XBackup.blockPlayerJoin = false
        XBackup.disableWatchdog = false
        XBackup.disableSaving = false
        XBackup.restoring = false

        running = true
        stopped = false
        runServer()
    }

    inline fun MinecraftServer.broadcast(text: Component) {
        playerList.broadcastSystemMessage(text, false)
    }

    fun isFileInWorld(world: ServerLevel, p: Path): Boolean {
        val path = DimensionType.getStorageFolder(
            world.dimension(),
            world.server.getWorldPath(LevelResource.ROOT).toAbsolutePath()
        ).normalize()
        return p.normalize().startsWith(path)
    }
}
