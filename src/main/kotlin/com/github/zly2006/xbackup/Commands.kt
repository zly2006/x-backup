package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.Utils.broadcast
import com.github.zly2006.xbackup.Utils.finishRestore
import com.github.zly2006.xbackup.Utils.save
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.Utils.setAutoSaving
import com.github.zly2006.xbackup.api.IBackup
import com.github.zly2006.xbackup.gui.RestoreInfoScreen
import com.github.zly2006.xbackup.ktdsl.BuilderScope
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
//? if >=1.21.11 {
/*import net.minecraft.command.DefaultPermissions
*///?}
import net.minecraft.command.argument.ColumnPosArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import net.minecraft.util.WorldSavePath
import net.minecraft.world.dimension.DimensionType
import java.net.URI
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
    }
    else {
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

fun sizeToString(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0

    return when {
        tb >= 1 -> "${String.format("%.2f", tb)} TB"
        gb >= 1 -> "${String.format("%.2f", gb)} GB"
        mb >= 1 -> "${String.format("%.2f", mb)} MB"
        kb >= 1 -> "${String.format("%.2f", kb)} KB"
        else -> "$bytes B"
    }
}

fun sizeText(bytes: Long) = Text.literal(sizeToString(bytes)).formatted(Formatting.GREEN)!!

fun MutableText.hover(literalText: MutableText) {
    styled {
        it.withHoverEvent(
            //? if >=1.21.5 {
            HoverEvent.ShowText(literalText)
            //?} else {
            /*HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                literalText
            )
            *///?}
        )
    }
}

fun MutableText.clickRun(cmd: String) {
    styled {
        it.withClickEvent(
            //? if >=1.21.5 {
            ClickEvent.RunCommand(cmd)
            //?} else {
            /*ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                cmd
            )
            *///?}
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
                    fun basicStatus(source: ServerCommandSource) {
                        source.send(Utils.translate("command.xb.status", if (XBackup.isBusy) "Busy" else "OK"))
                        if (XBackup.config.mirrorMode) {
                            source.send(Text.literal("X Backup is in mirror mode").formatted(Formatting.GOLD))
                        }
                        source.send(
                            Utils.translate(
                                "command.xb.background_task_status",
                                XBackup.backgroundState.toString()
                            )
                        )
                        if (XBackup.service.activeTaskProgress != -1) {
                            source.send(Text.literal("云备份任务：${XBackup.service.activeTask} ${XBackup.service.activeTaskProgress}%"))
                            source.send(networkStatsText())
                        }
                    }
                    executes {
                        basicStatus(it.source)
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
                    literal("--no-db") {
                        executes {
                            basicStatus(it.source)
                            1
                        }
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
                            }
                            else {
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
                        it.source.send(
                            Utils.translate("command.xb.version", XBackup.MOD_VERSION + "(" + XBackup.GIT_COMMIT + ")")
                                .styled {
                                    it.withClickEvent(
                                        //? if >=1.21.5 {
                                        ClickEvent.OpenUrl(URI("https://github.com/zly2006/x-backup"))
                                        //?} else {
                                        /*ClickEvent(
                                            ClickEvent.Action.OPEN_URL,
                                            "https://github.com/zly2006/x-backup"
                                        )
                                        *///?}
                                    )
                                }
                        )
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
        }
        else {
            registerBackupMode(dispatcher)
        }
    }

    private fun registerMirrorMode(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register {
            literal("mirror") {
                fun CommandContext<ServerCommandSource>.backup(): IBackup {
                    val id = try {
                        IntegerArgumentType.getInteger(this, "id")
                    } catch (_: IllegalArgumentException) {
                        runBlocking {
                            XBackup.service.getLatestBackup()?.id ?: error("No backups found")
                        }
                    }
                    return getBackup(id)
                }
                optional(argument("id", IntegerArgumentType.integer(1))) {
                    requires = checkPermission("x_backup.mirror", 0)
                    executes {
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        doRestore(it.backup(), it, path, forceStop = true)
                        1
                    }
                    literal("--restart").executes {
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        doRestore(it.backup(), it, path, forceStop = false)
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
                                I18n["command.xb.manual_backup"]
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
                                )
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
                                    XBackup.service.launch(Dispatchers.Default) {
                                        it.source.server.broadcast(
                                            Utils.translate(
                                                "command.xb.uploading_backup",
                                                backupIdText(id)
                                            )
                                        )
                                        XBackup.isBusy = false
                                        XBackup.service.cloudStorageProvider.uploadBackup(XBackup.service, id)
                                        it.source.server.broadcast(
                                            Utils.translate(
                                                "command.xb.backup_uploaded",
                                                backupIdText(id)
                                            )
                                        )
                                    }
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
                        literal("--mca") {
                            regionalRestoreArgs(RegionalRestoreMode.MCA)
                        }
                        literal("--chunk") {
                            regionalRestoreArgs(RegionalRestoreMode.CHUNK)
                        }
                        literal("--restart").executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                            val backup = getBackup(id)
                            doRestore(backup, it, path, forceStop = false)
                            1
                        }
                        literal("--force") {
                            requires = checkPermission("x_backup.restore.force", 4)
                            executes {
                                val id = IntegerArgumentType.getInteger(it, "id")
                                val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                                val backup = getBackup(id)
                                doRestore(backup, it, path, recheck = false, forceStop = true)
                                1
                            }
                        }
                    }.executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        val backup = getBackup(id)
                        doRestore(backup, it, path, forceStop = true)
                        1
                    }
                }
                literal("reload-config") {
                    requires = checkPermission("x_backup.reload_config", 4)
                    executes {
                        XBackup.loadConfig()
                        it.source.send(Utils.translate("command.xb.config_reloaded"))
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
                    literal("rm-unused-blobs") {
                        executes {
                            XBackup.ensureNotBusy {
                                val result = XBackup.service.deleteUnusedBlobs()
                                it.source.send(Text.literal("Deleted $result unused blobs"))
                            }
                            1
                        }
                    }
                    literal("upload") {
                        argument("id", IntegerArgumentType.integer(1)).executes {
                            val id = IntegerArgumentType.getInteger(it, "id")
                            val backup = getBackup(id)
                            if (backup.cloudBackupUrl != null) {
                                it.source.send(Utils.translate("command.xb.backup_already_uploaded", backupIdText(id)))
                                return@executes 0
                            }
                            XBackup.ensureNotBusy(Dispatchers.IO) {
                                it.source.send(Utils.translate("command.xb.uploading_backup", backupIdText(id)))
                                XBackup.isBusy = false
                                val result =
                                    XBackup.service.cloudStorageProvider.uploadBackup(XBackup.service, backup.id)
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
                                }
                                else {
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
                            it.source.send(
                                Utils.translate(
                                    "command.xb.set_backup_interval",
                                    XBackup.config.backupInterval
                                )
                            )
                            1
                        }
                    }
                    executes {
                        it.source.send(
                            Utils.translate(
                                "command.xb.current_backup_interval",
                                XBackup.config.backupInterval
                            )
                        )
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

    private fun BuilderScope<ServerCommandSource>.regionalRestoreArgs(mode: RegionalRestoreMode) {
        requires = checkPermission("x_backup.restore.regional", 4)
        argument("from", ColumnPosArgumentType.columnPos()) {
            argument("to", ColumnPosArgumentType.columnPos()).executes {
                runRegionalRestore(it, mode, useBlockCoords = false)
                1
            }
        }
        literal("--block") {
            argument("from", ColumnPosArgumentType.columnPos()) {
                argument("to", ColumnPosArgumentType.columnPos()).executes {
                    runRegionalRestore(it, mode, useBlockCoords = true)
                    1
                }
            }
        }
    }

    private fun runRegionalRestore(
        ctx: CommandContext<ServerCommandSource>,
        mode: RegionalRestoreMode,
        useBlockCoords: Boolean,
    ) {
        val id = IntegerArgumentType.getInteger(ctx, "id")
        val from = ColumnPosArgumentType.getColumnPos(ctx, "from")
        val to = ColumnPosArgumentType.getColumnPos(ctx, "to")
        val path = ctx.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()
        val worldSaveDir = DimensionType.getSaveDirectory(ctx.source.world.registryKey, path).normalize()
        val isOverworld = worldSaveDir == path
        val backup = getBackup(id)
        val range = RegionalRestore.resolveRange(mode, useBlockCoords, from.x, from.z, to.x, to.z)
        if (useBlockCoords) {
            val minX = min(from.x, to.x)
            val maxX = max(from.x, to.x)
            val minZ = min(from.z, to.z)
            val maxZ = max(from.z, to.z)
            when (mode) {
                RegionalRestoreMode.MCA -> XBackup.log.info(
                    "[X Backup] Block range ($minX, $minZ)-($maxX, $maxZ) -> MCA (${range.minX}, ${range.minZ}) to (${range.maxX}, ${range.maxZ})"
                )
                RegionalRestoreMode.CHUNK -> XBackup.log.info(
                    "[X Backup] Block range ($minX, $minZ)-($maxX, $maxZ) -> chunks (${range.minX}, ${range.minZ}) to (${range.maxX}, ${range.maxZ})"
                )
            }
        }
        when (mode) {
            RegionalRestoreMode.MCA -> {
                XBackup.log.info(
                    "[X Backup] Regional restore mode: MCA, (${range.minX}, ${range.minZ}) to (${range.maxX}, ${range.maxZ})"
                )
                logMcaFiles(backup, path, worldSaveDir, isOverworld, range)
                doRestore(backup, ctx, path, forceStop = true) { relative ->
                    regionalFileInRange(path, worldSaveDir, isOverworld, relative) { coords, extension ->
                        when (extension) {
                            "mca" -> range.contains(coords.first, coords.second)
                            "mcc" -> range.contains(coords.first shr 5, coords.second shr 5)
                            else -> false
                        }
                    }
                }
            }
            RegionalRestoreMode.CHUNK -> {
                XBackup.log.info(
                    "[X Backup] Regional restore mode: CHUNK, (${range.minX}, ${range.minZ}) to (${range.maxX}, ${range.maxZ})"
                )
                doRestore(
                    backup, ctx, path, forceStop = true,
                    filter = { relative ->
                        regionalFileInRange(path, worldSaveDir, isOverworld, relative) { coords, extension ->
                            extension == "mcc" && range.contains(coords.first, coords.second)
                        }
                    },
                    afterRestore = {
                        mergeSelectedChunks(backup, path, worldSaveDir, range)
                    }
                )
            }
        }
    }

    private fun regionalFileInRange(
        worldRoot: Path,
        worldSaveDir: Path,
        isOverworld: Boolean,
        relative: Path,
        predicate: (coords: Pair<Int, Int>, extension: String) -> Boolean,
    ): Boolean {
        val absolute = worldRoot.resolve(relative).normalize()
        if (!RegionalRestore.isPathInWorldSave(absolute, worldSaveDir, isOverworld)) {
            XBackup.log.debug("[X Backup] {} is not in world {}, skipping", absolute, worldSaveDir)
            return false
        }
        if (!RegionalRestore.isRegionStorageFile(relative)) {
            return false
        }
        val coords = RegionalRestore.parseRegionCoords(absolute.fileName.toString()) ?: return false
        return predicate(coords, absolute.extension)
    }

    private fun logMcaFiles(
        backup: IBackup,
        worldRoot: Path,
        worldSaveDir: Path,
        isOverworld: Boolean,
        range: RegionalRestore.Range,
    ) {
        val backupMca = backup.entries.mapNotNull { entry ->
            if (entry.isDirectory) return@mapNotNull null
            val relative = RegionalRestore.entryPath(entry.path)
            if (!regionalFileInRange(worldRoot, worldSaveDir, isOverworld, relative) { coords, extension ->
                    extension == "mca" && range.contains(coords.first, coords.second)
                }
            ) {
                return@mapNotNull null
            }
            relative.toString()
        }
        val worldMca = RegionalRestore.REGION_DIRS.flatMap { dir ->
            val dirPath = worldSaveDir.resolve(dir)
            if (!dirPath.exists() || !dirPath.isDirectory()) return@flatMap emptyList()
            dirPath.listDirectoryEntries("*.mca").map { it.fileName.toString() }
        }
        val plan = RegionalRestore.planMcaLogs(backupMca, worldMca, range)
        for ((x, z) in plan.restore) {
            XBackup.log.info(RegionalRestore.formatUnitLog("MCA", x, z, restoring = true))
        }
        for ((x, z) in plan.delete) {
            XBackup.log.info(RegionalRestore.formatUnitLog("MCA", x, z, restoring = false))
        }
    }

    private fun mergeSelectedChunks(
        backup: IBackup,
        worldRoot: Path,
        worldSaveDir: Path,
        range: RegionalRestore.Range,
    ) {
        val backupByPath = backup.entries.asSequence()
            .filter { !it.isDirectory }
            .associateBy { RegionalRestore.entryPath(it.path) }
        val minRX = range.minX shr 5
        val maxRX = range.maxX shr 5
        val minRZ = range.minZ shr 5
        val maxRZ = range.maxZ shr 5
        for (rx in minRX..maxRX) {
            for (rz in minRZ..maxRZ) {
                val chunkActions = linkedMapOf<Pair<Int, Int>, RegionFileMerger.ChunkAction>()
                for (dir in RegionalRestore.REGION_DIRS) {
                    val worldFile = worldSaveDir.resolve(dir).resolve(RegionalRestore.regionFileName(rx, rz))
                    val fromRoot = worldRoot.relativize(worldFile).normalize()
                    val entry = backupByPath[fromRoot]
                    val backupBytes = if (entry == null) {
                        null
                    } else {
                        val bytes = entry.getInputStream(XBackup.service)?.use { it.readBytes() }
                        if (bytes == null) {
                            val message = "[X Backup] Failed to read backup ${entry.path}"
                            XBackup.log.error(message)
                            throw IllegalStateException(message)
                        }
                        if (bytes.size.toLong() != entry.size) {
                            val message =
                                "[X Backup] Backup ${entry.path} size mismatch: ${bytes.size} != ${entry.size}"
                            XBackup.log.error(message)
                            throw IllegalStateException(message)
                        }
                        bytes
                    }
                    if (backupBytes == null && !worldFile.exists()) continue
                    try {
                        RegionFileMerger.merge(
                            current = worldFile.takeIf { it.exists() },
                            backupBytes = backupBytes,
                            output = worldFile,
                            regionX = rx,
                            regionZ = rz,
                            minChunkX = range.minX,
                            maxChunkX = range.maxX,
                            minChunkZ = range.minZ,
                            maxChunkZ = range.maxZ,
                        ) { chunkX, chunkZ, action ->
                            val key = chunkX to chunkZ
                            chunkActions[key] = RegionFileMerger.combineAction(chunkActions[key], action)
                        }
                    } catch (e: RegionFileMerger.UnreadableRegionFileException) {
                        val message = "[X Backup] Refusing to merge $fromRoot: ${e.message}"
                        XBackup.log.error(message, e)
                        throw IllegalStateException(message, e)
                    }
                }
                for ((coord, action) in chunkActions) {
                    XBackup.log.info(
                        RegionalRestore.formatUnitLog(
                            "chunk",
                            coord.first,
                            coord.second,
                            restoring = action == RegionFileMerger.ChunkAction.RESTORED,
                        )
                    )
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
        afterRestore: () -> Unit = {},
        filter: (Path) -> Boolean = { true },
    ) {
        val service = XBackup.service
        // Note: on server thread
        if (recheck && !service.check(backup)) {
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
                service.createBackup(path.normalize(), "Auto-backup before restoring to #${backup.id}", true)
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
                        service.restore(backup.id, path.normalize()) { !filter(it) }
                        afterRestore()
                        XBackup.reason = "Restoring backup #${backup.id} finished, launching/stopping server"
                        if (forceStop) {
                            forceStopServer()
                        }
                    }
                } catch (e: Throwable) {
                    XBackup.log.error("[X Backup] Error while restoring backup #${backup.id}", e)
                    return@a
                } finally {
                    if (!forceStop && !it.isSingleplayer) {
                        // restart the server
                        XBackup.log.info("[X Backup] Restarting server...")
                        XBackup.isBusy = false
                        XBackup.restoring = false
                        it.finishRestore()
                    } else if (!forceStop) {
                        RestoreInfoScreen.open(backup, path)
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
            //? if >=1.21.11 {
            /*val permission = when {
                defaultLevel <= 0 -> null
                defaultLevel <= 1 -> DefaultPermissions.MODERATORS
                defaultLevel <= 2 -> DefaultPermissions.GAMEMASTERS
                defaultLevel <= 3 -> DefaultPermissions.ADMINS
                else -> DefaultPermissions.OWNERS
            }
            permission == null || source.permissions.hasPermission(permission)
            *///?} else {
            source.hasPermissionLevel(defaultLevel)
            //?}
        }
    }
}

fun forceStopServer(): Nothing {
    Thread {
        Thread.sleep(5000)
        XBackup.log.error("[X Backup] Server did not stop in 5 seconds, force stopping")
        Runtime.getRuntime().halt(233)
    }.start()
    exitProcess(233)
}
