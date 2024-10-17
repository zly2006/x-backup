package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.Utils.finishRestore
import com.github.zly2006.xbackup.Utils.prepareRestore
import com.github.zly2006.xbackup.Utils.runCommand
import com.github.zly2006.xbackup.Utils.save
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.Utils.setAutoSaving
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.redenmc.bragadier.ktdsl.register
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import java.text.SimpleDateFormat

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
                    argument("comment", StringArgumentType.word()).executes {
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        val comment = StringArgumentType.getString(it, "comment")
                        XBackup.ensureNotBusy {
                            it.source.server.setAutoSaving(false)
                            it.source.server.save()

                            val result = XBackup.service.createBackup(path, "$comment by ${it.source.name}") { true }
                            it.source.send(
                                Text.of(
                                    "Backup #${result.backId} finished, ${sizeToString(result.totalSize)} " +
                                            "(${sizeToString(result.compressedSize)} after compression) " +
                                            "+${sizeToString(result.addedSize)}"
                                )
                            )
                            it.source.server.setAutoSaving(true)
                        }
                        1
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
                            it.source.send(Text.of("Backup #$id deleted"))
                        }
                        1
                    }
                }
                literal("restore") {
                    argument("id", IntegerArgumentType.integer(1)).executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        XBackup.ensureNotBusy {
                            if (XBackup.service.getBackup(id) == null) {
                                it.source.sendError(Text.of("Backup #$id not found"))
                                return@ensureNotBusy
                            }
                            try {
                                it.source.server.setAutoSaving(false)
                                it.source.server.prepareRestore("Restoring backup #$id")

                                runBlocking {
                                    XBackup.reason = "Auto-backup before restoring to #$id"
                                    XBackup.service.createBackup(path, "Auto-backup before restoring to #$id") { true }
                                    XBackup.reason = "Restoring backup #$id"
                                    val result = XBackup.service.restore(id, path) { false }
                                    XBackup.reason = "Restoring backup #$id finished, launching server"
                                }
                            } finally {
                                it.source.server.finishRestore()
                                it.source.server.setAutoSaving(true)
                            }
                        }
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

                            it.source.send(Text.of(Json.encodeToString(backup.toModel())))
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
                                it.source.send(Text.of("No backups found"))
                            } else {
                                it.source.send(Text.of("Backups:"))
                                backups.forEach { backup ->
                                    it.source.send(
                                        Text.of(
                                            "  #${backup.id.value} ${backup.comment} " +
                                                    "(${sizeToString(backup.size)} " +
                                                    "(${sizeToString(backup.zippedSize)} after compression) on " +
                                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(backup.created)
                                        ).copy().runCommand("/xb info ${backup.id.value}")
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
                                Text.of(
                                    "Backup #$id: ${backup.comment} " +
                                            "(${sizeToString(backup.size)} " +
                                            "(${sizeToString(backup.zippedSize)} after compression) on " +
                                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(backup.created)
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
}
