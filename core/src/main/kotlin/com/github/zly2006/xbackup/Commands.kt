package com.github.zly2006.xbackup

import RestartUtils
import com.github.zly2006.xbackup.Utils.broadcast
import com.github.zly2006.xbackup.Utils.finishRestore
import com.github.zly2006.xbackup.Utils.save
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.Utils.setAutoSaving
import com.github.zly2006.xbackup.ktdsl.register
import com.github.zly2006.xbackup.multi.IColumnPos
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.command.argument.ColumnPosArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Util
import net.minecraft.util.WorldSavePath
import java.nio.file.Path
import java.text.SimpleDateFormat
import kotlin.io.path.extension
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

fun literalText(text: String) = CrossVersionText.LiteralText(text)

object Commands {
    fun sizeToString(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val tb = gb / 1024.0

        return when {
            tb >= 1 -> "${String.format("%.1f", tb)} TB"
            gb >= 1 -> "${String.format("%.1f", gb)} GB"
            mb >= 1 -> "${String.format("%.1f", mb)} MB"
            kb >= 1 -> "${String.format("%.1f", kb)} KB"
            else -> "$bytes B"
        }
    }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register {
            literal("xb") {
                literal("status") {
                    executes {
                        it.source.send(literalText("X Backup status: " + if (XBackup.isBusy) "Busy" else "OK"))
                        runBlocking {
                            val latest = XBackup.service.getLatestBackup()
                            if (latest != null) {
                                it.source.send(
                                    CrossVersionText.ClickableText(
                                        literalText("Latest backup: #${latest.id} ${latest.comment}"),
                                        "/xb info ${latest.id}",
                                        literalText("Click to view details")
                                    )
                                )
                                if (XBackup.config.backupInterval != 0 && !XBackup.config.mirrorMode) {
                                    val next = latest.created + XBackup.config.backupInterval * 1000
                                    it.source.send(
                                        literalText(
                                            "Next backup at: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(next)
                                        )
                                    )
                                }
                            }
                            else {
                                it.source.send(literalText("No backups yet"))
                            }
                        }
                        1
                    }
                }
                literal("list") {
                    optional(argument("offset", IntegerArgumentType.integer(0))) {
                        executes {
                            val offset = try {
                                IntegerArgumentType.getInteger(it, "offset")
                            } catch (_: IllegalArgumentException) {
                                0
                            }
                            val backups = XBackup.service.listBackups(offset, 6)
                            if (backups.isEmpty()) {
                                it.source.send(literalText("No backups found"))
                            }
                            else {
                                it.source.send(literalText("Backups:"))
                                backups.forEach { backup ->
                                    it.source.send(
                                        CrossVersionText.ClickableText(
                                            literalText(
                                                "  #${backup.id} ${backup.comment} " +
                                                        "${sizeToString(backup.size)} on " +
                                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(backup.created)
                                            ),
                                            "/xb info ${backup.id}",
                                            literalText("Click to view details")
                                        )
                                    )
                                }
                            }
                            1
                        }
                    }
                }
                literal("version") {
                    executes {
                        it.source.send(literalText("X Backup https://github.com/zly2006/x-backup"))
                        it.source.send(literalText("MultiVersion: ${Utils.service.implementationTitle}"))
                        1
                    }
                }
                literal("info") {
                    argument("id", IntegerArgumentType.integer(1)).executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        XBackup.ensureNotBusy {
                            val backup = XBackup.service.getBackup(id)
                            if (backup == null) {
                                it.source.sendError(Text.of("Backup #$id not found"))
                                return@ensureNotBusy
                            }

                            it.source.send(
                                CrossVersionText.CombinedText(
                                    literalText(
                                        "Backup #$id: ${backup.comment} " +
                                                "(${sizeToString(backup.size)} " +
                                                "(${sizeToString(backup.zippedSize)} after compression) on " +
                                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(backup.created) + "\n"
                                    ),
                                    CrossVersionText.ClickableText(
                                        literalText("Delete"),
                                        "/xb delete $id",
                                        literalText("Click to delete")
                                    ),
                                    CrossVersionText.ClickableText(
                                        literalText("  Restore"),
                                        "/xb restore $id",
                                        literalText("Click to restore")
                                    ),
                                )
                            )
                        }
                        1
                    }
                }
            }
        }
        if (XBackup.config.mirrorMode) {
            registerMirrorMode(dispatcher)
        } else {
            registerBackupMode(dispatcher)
        }
    }

    private fun registerMirrorMode(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register {
            literal("mirror") {
                argument("id", IntegerArgumentType.integer(1)) {
                    literal("--stop").executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        doRestore(id, it, path, forceStop = true)
                        1
                    }
                }.executes {
                    val id = IntegerArgumentType.getInteger(it, "id")
                    val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                    doRestore(id, it, path)
                    1
                }
            }
        }
    }

    private fun registerBackupMode(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register {
            literal("xb") {
                literal("create") {
                    optional(argument("comment", StringArgumentType.greedyString())) {
                        executes {
                            val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                            val comment = try {
                                StringArgumentType.getString(it, "comment")
                            } catch (_: IllegalArgumentException) {
                                "Manual backup"
                            }
                            XBackup.ensureNotBusy {
                                it.source.server.broadcast(
                                    literalText(
                                        "${it.source.name} is creating a backup, this may take a while..."
                                    )
                                )
                                it.source.server.save()
                                it.source.server.setAutoSaving(false)
                                XBackup.disableSaving = true
                                val result =
                                    XBackup.service.createBackup(path, "$comment by ${it.source.name}") { true }
                                it.source.server.broadcast(
                                    literalText(
                                        "Backup #${result.backId} by ${it.source.name} finished, ${sizeToString(result.totalSize)} " +
                                                "(${sizeToString(result.compressedSize)} after compression) " +
                                                "+${sizeToString(result.addedSize)} " +
                                                "in ${result.millis}ms"
                                    )
                                )
                                XBackup.disableSaving = false
                                it.source.server.setAutoSaving(true)
                            }
                            1
                        }
                    }
                }
                literal("delete") {
                    argument("id", IntegerArgumentType.integer(1)).executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        XBackup.ensureNotBusy {
                            if (XBackup.service.getBackup(id) == null) {
                                it.source.sendError(Text.of("Backup #$id not found"))
                                return@ensureNotBusy
                            }
                            XBackup.service.deleteBackup(id)
                            it.source.send(literalText("Backup #$id deleted"))
                        }
                        1
                    }
                }
                literal("restore") {
                    argument("id", IntegerArgumentType.integer(1)) {
                        literal("--chunk") {
                            argument("from", ColumnPosArgumentType.columnPos()) {
                                argument("to", ColumnPosArgumentType.columnPos()).executes {
                                    val id = IntegerArgumentType.getInteger(it, "id")
                                    val from = ColumnPosArgumentType.getColumnPos(it, "from") as IColumnPos
                                    val to = ColumnPosArgumentType.getColumnPos(it, "to") as IColumnPos
                                    val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()
                                    val world = it.source.world
                                    doRestore(id, it, path) {
                                        val p = path.resolve(it).normalize()
                                        if (!Utils.service.isFileInWorld(world, p)) {
                                            XBackup.log.debug("[X Backup] {} is not in world {}, skipping", p, world)
                                            return@doRestore false
                                        }
                                        val minX = min(from.`x$x_backup`(), to.`x$x_backup`())
                                        val maxX = max(from.`x$x_backup`(), to.`x$x_backup`())
                                        val minZ = min(from.`z$x_backup`(), to.`z$x_backup`())
                                        val maxZ = max(from.`z$x_backup`(), to.`z$x_backup`())
                                        when (p.extension) {
                                            "mca" -> {
                                                val x = p.fileName.toString().split(".")[1].toInt()
                                                val z = p.fileName.toString().split(".")[2].toInt()
                                                if (x >= minX shr 9 && x <= maxX shr 9 && z >= minZ shr 9 && z <= maxZ shr 9) {
                                                    return@doRestore true
                                                }
                                                XBackup.log.debug("[XB] {} is not in chunk range, skipping", p)
                                                false
                                            }
                                            "mcc" -> {
                                                val x = p.fileName.toString().split(".")[1].toInt()
                                                val z = p.fileName.toString().split(".")[2].toInt()
                                                if (x >= minX shr 4 && x <= maxX shr 4 && z >= minZ shr 4 && z <= maxZ shr 4) {
                                                    return@doRestore true
                                                }
                                                XBackup.log.debug("[XB] {} is not in chunk range, skipping", p)
                                                false
                                            }
                                            else -> false
                                        }
                                    }
                                    1
                                }
                            }
                        }
                        literal("--stop").executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                            doRestore(id, it, path, forceStop = true)
                            1
                        }
                    }.executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        doRestore(id, it, path)
                        1
                    }
                }
                literal("debug") {
                    literal("inspect") {
                        // send the json details to the player
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            XBackup.ensureNotBusy {
                                val backup = XBackup.service.getBackup(id)
                                if (backup == null) {
                                    it.source.sendError(Text.of("Backup #$id not found"))
                                    return@ensureNotBusy
                                }

                                it.source.send(literalText(Json.encodeToString(backup)))
                            }
                            1
                        }
                    }
                    literal("upload") {
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            XBackup.ensureNotBusy {
                                val backup = XBackup.service.getBackup(id)
                                if (backup == null) {
                                    it.source.sendError(Text.of("Backup #$id not found"))
                                    return@ensureNotBusy
                                }

                                it.source.send(literalText("Uploading backup #$id..."))
                                val result = XBackup.service.oneDriveService.uploadOneDrive(XBackup.service, backup.id)
                                it.source.send(literalText("Backup #$id uploaded"))
                            }
                            1
                        }
                    }
                    literal("export") {
                    }
                    literal("restart") {
                        executes {
                            Thread {
                                when (Util.getOperatingSystem()) {
                                    Util.OperatingSystem.WINDOWS -> {
                                        it.source.server.stop(true)
                                        ProcessBuilder(
                                            RestartUtils.generateWindowsRestartCommand()
                                        ).start()
                                    }

                                    Util.OperatingSystem.LINUX, Util.OperatingSystem.OSX -> {
                                        it.source.server.stop(true)
                                        ProcessBuilder(
                                            RestartUtils.generateUnixRestartCommand()
                                        ).start()
                                    }

                                    else -> {
                                        return@Thread
                                    }
                                }
                                XBackup.log.info("[X Backup] Your game will restart soon...")
                                exitProcess(0)
                            }.start()
                            1
                        }
                    }
                }
                literal("backup-interval") {
                    argument("seconds", IntegerArgumentType.integer()) {
                        executes {
                            XBackup.config.backupInterval = it.getArgument("seconds", Int::class.java)
                            XBackup.saveConfig()
                            it.source.send(literalText("Backup interval set to " + XBackup.config.backupInterval + " seconds."))
                            1
                        }
                    }
                    executes {
                        it.source.send(literalText("Backup interval is " + XBackup.config.backupInterval + " seconds."))
                        1
                    }
                }
            }
        }
    }

    private fun doRestore(
        id: Int,
        it: CommandContext<ServerCommandSource>,
        path: Path,
        forceStop: Boolean = false,
        filter: (Path) -> Boolean = { true },
    ) {
        XBackup.reason = "Auto-backup before restoring to #$id"
        it.source.server.save()
        it.source.server.setAutoSaving(false)
        XBackup.disableSaving = true
        XBackup.disableWatchdog = true
        runBlocking {
            XBackup.service.createBackup(path, "Auto-backup before restoring to #$id") { true }
        }
        it.source.server.setAutoSaving(true)
        XBackup.disableSaving = false

        XBackup.ensureNotBusy(
            Dispatchers.IO // single player servers will stop when players exit, so we cant use the main thread
        ) {
            if (XBackup.service.getBackup(id) == null) {
                it.source.sendError(Text.of("Backup #$id not found"))
                return@ensureNotBusy
            }
            XBackup.restoring = true
            XBackup.serverStopHook = {
                try {
                    XBackup.serverStopHook = {}
                    runBlocking {
                        XBackup.reason = "Restoring backup #$id"
                        val result = XBackup.service.restore(id, path) { !filter(it) }
                        XBackup.reason = "Restoring backup #$id finished, launching server"
                    }
                } catch (e: Throwable) {
                    XBackup.log.error("[X Backup] Error while restoring backup #$id", e)
                } finally {
                    if (!forceStop && !it.isSingleplayer) {
                        // restart the server
                        XBackup.log.info("[X Backup] Restarting server...")
                        XBackup.isBusy = false
                        it.finishRestore()
                    }
                }
            }
            it.source.server.stop(false)
            XBackup.log.info("[X Backup] Waiting for server to stop...")
            it.source.server.thread.join()
        }
    }
}
