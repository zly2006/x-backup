package com.github.zly2006.xbackup

import RestartUtils
import com.github.zly2006.xbackup.Utils.broadcast
import com.github.zly2006.xbackup.Utils.finishRestore
import com.github.zly2006.xbackup.Utils.save
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.Utils.setAutoSaving
import com.github.zly2006.xbackup.ktdsl.register
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.command.argument.ColumnPosArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import net.minecraft.util.WorldSavePath
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.extension
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

fun dateTimeText(time: Long): MutableText =
    Text.literal(SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").apply {
        timeZone = TimeZone.getDefault()
    }.format(time)).formatted(Formatting.GOLD)

fun backupIdText(id: Int): MutableText = Text.literal("#$id").formatted(Formatting.AQUA)

private fun sizeToString(bytes: Long): String {
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

fun sizeText(bytes: Long): MutableText = Text.literal(sizeToString(bytes)).formatted(Formatting.GREEN)

fun MutableText.hover(literalText: MutableText) {
    styled {
        it.withHoverEvent(
            HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                literalText
            )
        )
    }
}

fun MutableText.clickRun(cmd: String) {
    styled {
        it.withClickEvent(
            ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                cmd
            )
        )
    }
}

object Commands {
    private fun getBackup(id: Int): BackupDatabaseService.Backup {
        return runBlocking {
            XBackup.service.getBackup(id)
        } ?: throw SimpleCommandExceptionType(
            Text.translatable("command.xb.backup_not_found", backupIdText(id))
        ).create()
    }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register {
            literal("xb") {
                literal("status") {
                    executes {
                        it.source.send(Text.translatable("command.xb.status", if (XBackup.isBusy) "Busy" else "OK"))
                        runBlocking {
                            val latest = XBackup.service.getLatestBackup()
                            if (latest != null) {
                                it.source.send(
                                    Text.translatable("command.xb.latest_backup", backupIdText(latest.id), latest.comment).apply {
                                        hover(Text.translatable("command.xb.click_view_details"))
                                        clickRun("/xb info ${latest.id}")
                                    }
                                )
                                if (XBackup.config.backupInterval != 0 && !XBackup.config.mirrorMode) {
                                    val next = latest.created + XBackup.config.backupInterval * 1000
                                    it.source.send(
                                        Text.translatable(
                                            "command.xb.next_backup",
                                            dateTimeText(next)
                                        )
                                    )
                                }
                            } else {
                                it.source.send(Text.translatable("command.xb.no_backups"))
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
                                it.source.send(Text.translatable("command.xb.no_backups_found"))
                            } else {
                                it.source.send(Text.translatable("command.xb.backups"))
                                backups.forEach { backup ->
                                    it.source.send(
                                        Text.translatable(
                                            "command.xb.backup_details",
                                            backupIdText(backup.id), backup.comment, sizeText(backup.size),
                                            dateTimeText(backup.created)
                                        ).apply {
                                            hover(Text.translatable("command.xb.click_view_details"))
                                            clickRun("/xb info ${backup.id}")
                                        }
                                    )
                                }
                            }
                            1
                        }
                    }
                }
                literal("version") {
                    executes {
                        it.source.send(Text.translatable("command.xb.version"))
                        1
                    }
                }
                literal("info") {
                    argument("id", IntegerArgumentType.integer(1)).executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        XBackup.ensureNotBusy {
                            val backup = getBackup(id)
                            it.source.send(
                                Text.translatable(
                                    "command.xb.backup_info",
                                    backupIdText(id), backup.comment, sizeText(backup.size),
                                    sizeText(backup.zippedSize),
                                    dateTimeText(backup.created)
                                ).apply {
                                    append(
                                        Text.translatable("command.xb.delete").apply {
                                            hover(Text.translatable("command.xb.click_delete"))
                                            clickRun("/xb delete $id")
                                        }
                                    )
                                    append(Text.translatable("command.xb.space"))
                                    append(
                                        Text.translatable("command.xb.restore").apply {
                                            hover(Text.translatable("command.xb.click_restore"))
                                            clickRun("/xb restore $id")
                                        }
                                    )
                                }
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
                                    Text.translatable("command.xb.creating_backup", it.source.name)
                                )
                                it.source.server.save()
                                it.source.server.setAutoSaving(false)
                                XBackup.disableSaving = true
                                val result =
                                    XBackup.service.createBackup(path, "$comment by ${it.source.name}") { true }
                                it.source.server.broadcast(
                                    Text.translatable(
                                        "command.xb.backup_finished",
                                        backupIdText(result.backId), it.source.name, sizeText(result.totalSize),
                                        sizeText(result.compressedSize), sizeText(result.addedSize), result.millis
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
                            XBackup.service.deleteBackup(getBackup(id).id)
                            it.source.send(Text.translatable("command.xb.backup_deleted", backupIdText(id)))
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
                                        if (!Utils.isFileInWorld(world, p)) {
                                            XBackup.log.debug("[X Backup] {} is not in world {}, skipping", p, world)
                                            return@doRestore false
                                        }
                                        val minX = min(from.x, to.z)
                                        val maxX = max(from.x, to.z)
                                        val minZ = min(from.x, to.z)
                                        val maxZ = max(from.x, to.z)
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
                                val backup = getBackup(id)
                                it.source.send(Text.translatable("command.xb.json_details", Json.encodeToString(backup)))
                            }
                            1
                        }
                    }
                    literal("upload") {
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            XBackup.ensureNotBusy {
                                val backup = getBackup(id)
                                it.source.send(Text.translatable("command.xb.uploading_backup", backupIdText(id)))
                                val result = XBackup.service.oneDriveService.uploadOneDrive(XBackup.service, backup.id)
                                it.source.send(Text.translatable("command.xb.backup_uploaded", backupIdText(id)))
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
                            it.source.send(Text.translatable("command.xb.set_backup_interval", XBackup.config.backupInterval))
                            1
                        }
                    }
                    executes {
                        it.source.send(Text.translatable("command.xb.current_backup_interval", XBackup.config.backupInterval))
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
        getBackup(id)
        // Note: on server thread
        XBackup.reason = "Auto-backup before restoring to #$id"
        XBackup.disableWatchdog = true
        it.source.server.save()
        it.source.server.setAutoSaving(false)
        XBackup.disableSaving = true
        runBlocking {
            XBackup.service.createBackup(path.normalize(), "Auto-backup before restoring to #$id") { true }
        }
        it.source.server.setAutoSaving(true)
        XBackup.disableSaving = false

        // Note: switch to IO thread context
        XBackup.ensureNotBusy(
            Dispatchers.IO // single player servers will stop when players exit, so we cant use the main thread
        ) {
            XBackup.restoring = true
            XBackup.serverStopHook = {
                try {
                    XBackup.serverStopHook = {}
                    runBlocking {
                        XBackup.reason = "Restoring backup #$id"
                        val result = XBackup.service.restore(id, path.normalize()) { !filter(it) }
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
