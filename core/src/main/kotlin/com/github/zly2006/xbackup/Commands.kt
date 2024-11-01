package com.github.zly2006.xbackup

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
import net.minecraft.util.WorldSavePath
import java.nio.file.Path
import java.text.SimpleDateFormat

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
                                it.source.server.save()
                                it.source.server.setAutoSaving(false)
                                XBackup.disableSaving = true
                                val result =
                                    XBackup.service.createBackup(path, "$comment by ${it.source.name}") { true }
                                it.source.send(
                                    literalText(
                                        "Backup #${result.backId} finished, ${sizeToString(result.totalSize)} " +
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
                            argument("chunk", ColumnPosArgumentType.columnPos()).executes {
                                val id = IntegerArgumentType.getInteger(it, "id")
                                val chunk = ColumnPosArgumentType.getColumnPos(it, "chunk")
                                val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()

                                doRestore(id, it, path, chunks = listOf(Pair(chunk.x shr 4, chunk.z shr 4)))
                                1
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
            }
        }
    }

    private fun doRestore(
        id: Int,
        it: CommandContext<ServerCommandSource>,
        path: Path,
        forceStop: Boolean = false,
        chunks: List<Pair<Int, Int>>? = null
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
                    XBackup.service.restore(id, path) { false }
                    it.source.server.stop(false)
                    return@ensureNotBusy
                }

                runBlocking {
                    XBackup.reason = "Restoring backup #$id"
                    val result = XBackup.service.restore(id, path) { false }
                    XBackup.reason = "Restoring backup #$id finished, launching server"
                }
            } finally {
                it.source.server.finishRestore()
                it.source.server.setAutoSaving(true)
            }
        }
    }
}
