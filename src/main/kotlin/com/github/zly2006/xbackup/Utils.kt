package com.github.zly2006.xbackup

import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import net.minecraft.world.dimension.DimensionType
import java.nio.file.Path

@Suppress("NOTHING_TO_INLINE")
object Utils {
    inline fun translate(key: String, vararg args: Any): MutableText {
        return Text.translatableWithFallback(
            key,
            I18n[key],
            *args
        )
    }

    inline fun ServerCommandSource.send(text: Text) {
        sendMessage(text)
    }

    inline fun MinecraftServer.setAutoSaving(value: Boolean) {
        worlds.forEach { it.savingDisabled = !value }
    }

    inline fun MinecraftServer.save() {
        saveAll(false, false, true)
        //? if >=1.21.11 {
        /*syncChunkWrites()
        *///?}
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

    inline fun MinecraftServer.broadcast(text: Text) {
        playerManager.broadcast(text, false)
    }

    fun isFileInWorld(world: ServerWorld, p: Path): Boolean {
        val worldRoot = world.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()
        val worldSaveDir = DimensionType.getSaveDirectory(world.registryKey, worldRoot).normalize()
        return RegionalRestore.isPathInWorldSave(p, worldSaveDir, isOverworld = worldSaveDir == worldRoot)
    }
}
