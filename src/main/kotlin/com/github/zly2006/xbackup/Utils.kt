package com.github.zly2006.xbackup

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import net.minecraft.world.dimension.DimensionType
import java.nio.file.Path

@Suppress("NOTHING_TO_INLINE")
object Utils {
    val onedriveSupport = FabricLoader.getInstance().isModLoaded("x_backup_onedrive")

    inline fun ServerCommandSource.send(text: Text) {
        sendMessage(text)
    }

    inline fun MinecraftServer.setAutoSaving(value: Boolean) {
        worlds.forEach { it.savingDisabled = !value }
    }

    inline fun MinecraftServer.save() {
        saveAll(false, false, true)
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
        val path = DimensionType.getSaveDirectory(
            world.registryKey,
            world.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
        ).normalize()
        return p.normalize().startsWith(path)
    }
}
