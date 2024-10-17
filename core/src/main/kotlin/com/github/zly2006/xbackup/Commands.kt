package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.Utils.finishRestore
import com.github.zly2006.xbackup.Utils.prepareRestore
import com.github.zly2006.xbackup.Utils.save
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.Utils.setAutoSaving
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.redenmc.bragadier.ktdsl.register
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath

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

                }.executes {
                    val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                    XBackup.ensureNotBusy {
                        it.source.server.setAutoSaving(false)
                        it.source.server.save()

                        val result = XBackup.service.createBackup(path) { true }
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
                literal("delete") {

                }
                literal("restore") {
                    argument("id", IntegerArgumentType.integer(1)).executes {
                        val id = IntegerArgumentType.getInteger(it, "id")
                        val path = it.source.server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                        XBackup.ensureNotBusy {
                            it.source.server.setAutoSaving(false)
                            it.source.server.prepareRestore("Restoring backup #$id")

                            val result = XBackup.service.restore(id, path) { false }

                            it.source.server.finishRestore()
                            it.source.server.setAutoSaving(true)
                        }
                        1
                    }
                }
                literal("inspect") {

                }
                literal("list") {

                }
                literal("info") {

                }
                literal("export") {

                }
            }
        }
    }
}
