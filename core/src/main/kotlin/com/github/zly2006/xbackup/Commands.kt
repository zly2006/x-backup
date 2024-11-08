package com.github.zly2006.xbackup

import RestartUtils
import com.github.zly2006.xbackup.Utils.broadcast
import com.github.zly2006.xbackup.Utils.finishRestore
import com.github.zly2006.xbackup.Utils.prepareRestore
import com.github.zly2006.xbackup.Utils.save
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.Utils.setAutoSaving
import com.github.zly2006.xbackup.ktdsl.register
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
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
                                    val from = ColumnPosArgumentType.getColumnPos(it, "from")
                                    val to = ColumnPosArgumentType.getColumnPos(it, "to")
                                    val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()
                                    val world = it.source.world
                                    doRestore(id, it, path) {
                                        val p = path.resolve(it).normalize()
                                        if (!Utils.service.isFileInWorld(world, p)) {
                                            return@doRestore false
                                        }
                                        if (p.extension == "mca") {
                                            val x = p.fileName.toString().split(".")[1].toInt()
                                            val z = p.fileName.toString().split(".")[2].toInt()
                                            return@doRestore (x shr 9) >= min((from.x shr 9), (to.x shr 9)) && (x shr 9) <= max((from.x shr 9), (to.x shr 9)) && (z shr 9) >= min((from.z shr 9), (to.z shr 9)) && (z shr 9) <= max((from.z shr 9), (to.z shr 9))
                                        }
                                        else if (p.extension == "mcc") {
                                            val x = p.fileName.toString().split(".")[1].toInt()
                                            val z = p.fileName.toString().split(".")[2].toInt()
                                            return@doRestore (x shr 4) >= min((from.x shr 4), (to.x shr 4)) && (x shr 4) <= max((from.x shr 4), (to.x shr 4)) && (z shr 4) >= min((from.z shr 4), (to.z shr 4)) && (z shr 4) <= max((from.z shr 4), (to.z shr 4))
                                        }
                                        else {
                                            return@doRestore false
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
                literal("login") {
                    executes {
                        XBackup.ensureNotBusy {
                            XBackup.service.oneDriveService.initializeGraphForUserAuth(it.source, true)
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
            context = if (it.source.server.isSingleplayer) {
                Dispatchers.IO // single player servers will stop when players exit, so we cant use the main thread
            }
            else {
                it.source.server.asCoroutineDispatcher()
            }
        ) {
            if (XBackup.service.getBackup(id) == null) {
                it.source.sendError(Text.of("Backup #$id not found"))
                return@ensureNotBusy
            }
            try {
                it.source.server.prepareRestore("Restoring backup #$id")
                if (forceStop) {
                    XBackup.service.restore(id, path) { !filter(it) }
                    it.source.server.stop(false)
                    return@ensureNotBusy
                }

                runBlocking {
                    XBackup.reason = "Restoring backup #$id"
                    val result = XBackup.service.restore(id, path) { !filter(it) }
                    XBackup.reason = "Restoring backup #$id finished, launching server"
                }
            } finally {
                it.source.server.finishRestore()
                it.source.server.setAutoSaving(true)
            }
        }
    }
}
