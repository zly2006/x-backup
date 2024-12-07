package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.Utils.broadcast
import com.github.zly2006.xbackup.Utils.finishRestore
import com.github.zly2006.xbackup.Utils.save
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.Utils.setAutoSaving
import com.github.zly2006.xbackup.api.IBackup
import com.github.zly2006.xbackup.ktdsl.register
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import me.lucko.fabric.api.permissions.v0.Permissions
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
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

fun shortDateTimeText(time: Long): MutableText {
    val text = if (System.currentTimeMillis() - time < 24 * 3600 * 1000) {
        SimpleDateFormat("HH:mm").apply {
            timeZone = TimeZone.getDefault()
        }.format(time)
    } else {
        SimpleDateFormat("MM-dd HH:mm").apply {
            timeZone = TimeZone.getDefault()
        }.format(time)
    }
    return Text.literal(text).apply {
        hover(Text.literal(SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").apply {
            timeZone = TimeZone.getDefault()
        }.format(time)))
        formatted(Formatting.GOLD)!!
    }
}

fun backupIdText(id: Int) = Text.literal("#$id").formatted(Formatting.AQUA)!!

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

fun sizeText(bytes: Long) = Text.literal(sizeToString(bytes)).formatted(Formatting.GREEN)!!

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
    fun networkStatsText(): MutableText {
        val cloudStorage = XBackup.service.cloudStorageProvider
        return Text.empty().apply {
            append(Text.literal("⏶" + sizeToString(cloudStorage.bytesSentLastSecond) + "/s"))
            append(" ")
            append(Text.literal("⏷" + sizeToString(cloudStorage.bytesReceivedLastSecond) + "/s"))
        }
    }

    private fun getBackup(id: Int): IBackup {
        return XBackup.service.getBackup(id) ?: throw SimpleCommandExceptionType(
            Utils.translate("command.xb.backup_not_found", backupIdText(id))
        ).create()
    }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register {
            literal("xb") {
                literal("status") {
                    executes {
                        it.source.send(Utils.translate("command.xb.status", if (XBackup.isBusy) "Busy" else "OK"))
                        if (XBackup.config.mirrorMode) {
                            it.source.send(Text.literal("X Backup is in mirror mode").formatted(Formatting.GOLD))
                        }
                        it.source.send(Utils.translate("command.xb.background_task_status", XBackup.backgroundState.toString()))
                        if (XBackup.service.activeTaskProgress != -1) {
                            it.source.send(Text.literal("云备份任务：${XBackup.service.activeTask} ${XBackup.service.activeTaskProgress}%"))
                            it.source.send(networkStatsText())
                        }
                        GlobalScope.launch(it.source.server.asCoroutineDispatcher()) {
                            val status = XBackup.service.status()
                            it.source.send(
                                Utils.translate(
                                    "command.xb.statistics",
                                    status.backupCount, sizeText(status.blobDiskUsage), sizeText(status.actualUsage)
                                )
                            )
                            status.latestBackup?.let { latest ->
                                it.source.send(
                                    Utils.translate("command.xb.latest_backup", backupIdText(latest.id), latest.comment)
                                        .apply {
                                            hover(Utils.translate("command.xb.click_view_details"))
                                            clickRun("/xb info ${latest.id}")
                                        }
                                )
                                if (XBackup.config.backupInterval != 0 && !XBackup.config.mirrorMode) {
                                    val next = latest.created + XBackup.config.backupInterval * 1000
                                    it.source.send(
                                        Utils.translate(
                                            "command.xb.next_backup",
                                            shortDateTimeText(next)
                                        )
                                    )
                                }
                            } ?: run {
                                it.source.send(Utils.translate("command.xb.no_backups"))
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
                                it.source.send(Utils.translate("command.xb.no_backups_found"))
                            } else {
                                it.source.send(Utils.translate("command.xb.backups"))
                                backups.forEach { backup ->
                                    it.source.send(
                                        Utils.translate(
                                            "command.xb.backup_details",
                                            backupIdText(backup.id), backup.comment, sizeText(backup.size),
                                            shortDateTimeText(backup.created)
                                        ).apply {
                                            hover(Utils.translate("command.xb.click_view_details"))
                                            clickRun("/xb info ${backup.id}")
                                        }
                                    )
                                }
                                if (XBackup.service.backupCount() > offset + 6) {
                                    it.source.send(Utils.translate("command.xb.more_backups").apply {
                                        formatted(Formatting.GRAY)
                                        hover(Utils.translate("command.xb.click_view_more"))
                                        clickRun("/xb list ${offset + 6}")
                                    })
                                }
                            }
                            1
                        }
                    }
                }
                literal("version") {
                    executes {
                        it.source.send(Utils.translate("command.xb.version", XBackup.MOD_VERSION + "(" + XBackup.GIT_COMMIT + ")"))
                        1
                    }
                }
                literal("info") {
                    argument("id", IntegerArgumentType.integer(1)).executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        val backup = getBackup(id)
                        XBackup.ensureNotBusy {
                            it.source.send(
                                Utils.translate(
                                    "command.xb.backup_info",
                                    backupIdText(id), backup.comment, sizeText(backup.size),
                                    sizeText(backup.zippedSize),
                                    Text.literal(SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").apply {
                                        timeZone = TimeZone.getDefault()
                                    }.format(backup.created)).apply {
                                        formatted(Formatting.GOLD)!!
                                    }
                                ).apply {
                                    append("\n")
                                    append(
                                        Utils.translate("command.xb.delete").apply {
                                            hover(Utils.translate("command.xb.click_delete").formatted(Formatting.RED))
                                            clickRun("/xb delete $id")
                                            formatted(Formatting.RED)
                                        }
                                    )
                                    append(Utils.translate("command.xb.space"))
                                    append(
                                        Utils.translate("command.xb.restore").apply {
                                            hover(Utils.translate("command.xb.click_restore"))
                                            clickRun("/xb restore $id")
                                            formatted(Formatting.DARK_GREEN)
                                        }
                                    )
                                    if (backup.entries.all { it.isDirectory || it.cloudDriveId != null }) {
                                        append("\n")
                                        append(Text.literal("此存档已完成云备份。"))
                                    }
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
                optional(argument("id", IntegerArgumentType.integer(1))) {
                    requires = checkPermission("x_backup.mirror", 0)
                    executes {
                        val id = try {
                            IntegerArgumentType.getInteger(it, "id")
                        } catch (_: IllegalArgumentException) {
                            runBlocking {
                                XBackup.service.getLatestBackup()?.id ?: error("No backups found")
                            }
                        }
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        val backup = getBackup(id)
                        doRestore(backup, it, path)
                        1
                    }
                    literal("--stop").executes {
                        val id = try {
                            IntegerArgumentType.getInteger(it, "id")
                        } catch (_: IllegalArgumentException) {
                            runBlocking {
                                XBackup.service.getLatestBackup()?.id ?: error("No backups found")
                            }
                        }
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        val backup = getBackup(id)
                        doRestore(backup, it, path, forceStop = true)
                        1
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun registerBackupMode(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register {
            literal("xb") {
                literal("create") {
                    requires = checkPermission("x_backup.create", 0)
                    optional(argument("comment", StringArgumentType.greedyString())) {
                        executes {
                            val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()
                            val comment = try {
                                StringArgumentType.getString(it, "comment")
                            } catch (_: IllegalArgumentException) {
                                I18n.langMap["command.xb.manual_backup"] ?: "Manual backup"
                            }
                            XBackup.ensureNotBusy {
                                it.source.server.broadcast(
                                    Utils.translate("command.xb.creating_backup", it.source.name)
                                )
                                it.source.server.save()
                                it.source.server.setAutoSaving(false)
                                XBackup.disableSaving = true
                                val result = XBackup.service.createBackup(
                                    path,
                                    "$comment by ${it.source.name}",
                                    temporary = false,
                                    buildJsonObject {
                                        put("mod_ver", XBackup.MOD_VERSION)
                                        put("source", it.source.name)
                                    }
                                ) { true }
                                it.source.server.broadcast(
                                    Utils.translate(
                                        "command.xb.backup_finished",
                                        backupIdText(result.backId), it.source.name, sizeText(result.totalSize),
                                        sizeText(result.compressedSize), sizeText(result.addedSize), result.millis
                                    )
                                )
                                XBackup.disableSaving = false
                                it.source.server.setAutoSaving(true)
                                val id = result.backId
                                if (XBackup.config.cloudBackupToken != null) {
                                    it.source.send(Utils.translate("command.xb.uploading_backup", backupIdText(id)))
                                    XBackup.service.cloudStorageProvider.uploadBackup(XBackup.service, id)
                                    it.source.send(Utils.translate("command.xb.backup_uploaded", backupIdText(id)))
                                }
                            }
                            1
                        }
                    }
                }
                literal("delete") {
                    requires = checkPermission("x_backup.delete", 4)
                    argument("id", IntegerArgumentType.integer(1)).executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        val backup = getBackup(id)
                        XBackup.ensureNotBusy {
                            XBackup.service.deleteBackupInternal(backup)
                            it.source.send(Utils.translate("command.xb.backup_deleted", backupIdText(id)))
                        }
                        1
                    }
                }
                literal("restore") {
                    requires = checkPermission("x_backup.restore", 0)
                    argument("id", IntegerArgumentType.integer(1)) {
                        literal("--chunk") {
                            requires = checkPermission("x_backup.restore.regional", 4)
                            argument("from", ColumnPosArgumentType.columnPos()) {
                                argument("to", ColumnPosArgumentType.columnPos()).executes {
                                    val id = IntegerArgumentType.getInteger(it, "id")
                                    val from = ColumnPosArgumentType.getColumnPos(it, "from")
                                    val to = ColumnPosArgumentType.getColumnPos(it, "to")
                                    val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()
                                    val world = it.source.world
                                    val backup = getBackup(id)
                                    doRestore(backup, it, path) {
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
                            val backup = getBackup(id)
                            doRestore(backup, it, path, forceStop = true)
                            1
                        }
                        literal("--force") {
                            requires = checkPermission("x_backup.restore.force", 4)
                            executes {
                                val id = IntegerArgumentType.getInteger(it, "id")
                                val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                                val backup = getBackup(id)
                                doRestore(backup, it, path, recheck = false)
                                1
                            }
                        }
                    }.executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        val backup = getBackup(id)
                        doRestore(backup, it, path)
                        1
                    }
                }
                literal("debug") {
                    requires = checkPermission("x_backup.debug", 4)
                    literal("inspect") {
                        // send the json details to the player
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val backup = getBackup(id)
                            XBackup.ensureNotBusy {
                                val file = Path("export").resolve("backup-$id.json")
                                    .toAbsolutePath()
                                    .createParentDirectories()
                                file.outputStream().use {
                                    Json.encodeToStream(backup, it)
                                }
                                it.source.send(Text.literal("Saved backup details to $file"))
                            }
                            1
                        }
                    }
                    literal("upload") {
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val backup = getBackup(id)
                            if (backup.entries.all { it.isDirectory || it.cloudDriveId != null }) {
                                it.source.send(Utils.translate("command.xb.backup_already_uploaded", backupIdText(id)))
                                return@executes 0
                            }
                            XBackup.ensureNotBusy {
                                it.source.send(Utils.translate("command.xb.uploading_backup", backupIdText(id)))
                                val result = XBackup.service.cloudStorageProvider.uploadBackup(XBackup.service, backup.id)
                                it.source.send(Utils.translate("command.xb.backup_uploaded", backupIdText(id)))
                            }
                            1
                        }
                    }
                    literal("download") {
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val backup = getBackup(id)
                            XBackup.ensureNotBusy {
                                val total = backup.entries.count { !it.isDirectory }
                                var downloaded = 0
                                backup.entries.filter { !it.isDirectory }.forEach {
                                    it.getInputStream(XBackup.service)
                                    require(XBackup.service.getBlobFile(it.hash).fileSize() == it.zippedSize) {
                                        "$it is not fully uploaded"
                                    }
                                    downloaded++
                                    XBackup.service.activeTaskProgress = 100 * downloaded / total
                                }
                                it.source.send(Text.keybind("Debug: Downloaded backup $id"))
                                1
                            }
                            1
                        }
                    }
                    literal("export") {
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val backup = getBackup(id)
                            XBackup.ensureNotBusy {
                                it.source.send(Utils.translate("command.xb.exporting_backup", backupIdText(id)))
                                val result = XBackup.service.restore(
                                    backup.id,
                                    Path("export").resolve("backup-$id").normalize()
                                ) { true }
                                it.source.send(Utils.translate("command.xb.backup_exported", backupIdText(id)))
                            }
                            1
                        }
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
                    literal("check") {
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val backup = getBackup(id)
                            XBackup.ensureNotBusy {
                                it.source.send(Utils.translate("command.xb.checking_backup", backupIdText(id)))
                                val result = XBackup.service.check(backup)
                                if (result) {
                                    it.source.send(Utils.translate("command.xb.backup_ok", backupIdText(id)))
                                } else {
                                    it.source.send(Utils.translate("command.xb.backup_corrupted", backupIdText(id)))
                                }
                            }
                            1
                        }
                    }
                    literal("crontab-stop").executes {
                        runBlocking {
                            XBackup.crontabJob?.cancelAndJoin()
                        }
                        it.source.send(Text.literal("Stopped crontab job"))
                        1
                    }
                    literal("zip") {
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val backup = getBackup(id)
                            XBackup.ensureNotBusy {
                                it.source.send(Utils.translate("command.xb.zipping_backup", backupIdText(id)))
                                val file = Path("export").resolve("backup-$id.zip").createParentDirectories()
                                ZipOutputStream(file.outputStream()).use { stream ->
                                    XBackup.service.zipArchive(stream, backup)
                                }
                                it.source.send(Utils.translate("command.xb.backup_zipped", backupIdText(id)))
                            }
                            1
                        }
                    }
                }
                literal("backup-interval") {
                    argument("seconds", IntegerArgumentType.integer()) {
                        requires = checkPermission("x_backup.set_backup_interval")
                        executes {
                            XBackup.config.backupInterval = it.getArgument("seconds", Int::class.java)
                            XBackup.saveConfig()
                            it.source.send(Utils.translate("command.xb.set_backup_interval", XBackup.config.backupInterval))
                            1
                        }
                    }
                    executes {
                        it.source.send(Utils.translate("command.xb.current_backup_interval", XBackup.config.backupInterval))
                        1
                    }
                }
                literal("prune") {
                    requires = checkPermission("x_backup.prune")
                    executes {
                        GlobalScope.launch(it.source.server.asCoroutineDispatcher()) {
                            XBackup.prune(it.source.server)
                        }
                        1
                    }
                }
            }
        }
    }

    private fun doRestore(
        backup: IBackup,
        it: CommandContext<ServerCommandSource>,
        path: Path,
        forceStop: Boolean = false,
        recheck: Boolean = true,
        filter: (Path) -> Boolean = { true },
    ) {
        // Note: on server thread
        if (recheck && !XBackup.service.check(backup)) {
            it.source.sendError(Utils.translate("command.xb.backup_corrupted", backupIdText(backup.id)).apply {
                hover(Utils.translate("command.xb.backup_corrupted.force", backupIdText(backup.id)))
            })
            return
        }
        if (XBackup.config.backupBeforeRestore && !XBackup.config.mirrorMode) {
            XBackup.reason = "Auto-backup before restoring to #${backup.id}"
            XBackup.disableWatchdog = true
            it.source.server.save()
            it.source.server.setAutoSaving(false)
            XBackup.disableSaving = true
            runBlocking {
                XBackup.service.createBackup(path.normalize(), "Auto-backup before restoring to #${backup.id}", true) { true }
            }
            it.source.server.setAutoSaving(true)
            XBackup.disableSaving = false
        }

        // Note: switch to IO thread context
        XBackup.ensureNotBusy(
            Dispatchers.IO // single player servers will stop when players exit, so we cant use the main thread
        ) {
            XBackup.restoring = true
            XBackup.serverStopHook = a@{
                try {
                    XBackup.serverStopHook = {}
                    runBlocking {
                        XBackup.reason = "Restoring backup #${backup.id}"
                        val result = XBackup.service.restore(backup.id, path.normalize()) { !filter(it) }
                        XBackup.reason = "Restoring backup #${backup.id} finished, launching server"
                    }
                } catch (e: Throwable) {
                    XBackup.log.error("[X Backup] Error while restoring backup #${backup.id}", e)
                    return@a
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
            it.source.server.networkIo?.connections?.forEach {
                it.disconnected = true
                // prevent the game from saving player data again
            }
            XBackup.log.info("[X Backup] Waiting for server to stop...")
            it.source.server.thread.join()
        }
    }

    private fun checkPermission(perm: String, defaultLevel: Int = 2): (ServerCommandSource) -> Boolean = { source ->
        try {
            // Call fabric-permissions API, but it might not be available
            Permissions.check(source, perm, defaultLevel)
        } catch (e: NoClassDefFoundError) {
            // If the API is not available, just return true
            source.hasPermissionLevel(defaultLevel)
        }
    }
}
