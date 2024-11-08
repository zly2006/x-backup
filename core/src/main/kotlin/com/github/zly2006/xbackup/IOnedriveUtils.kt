package com.github.zly2006.xbackup

import net.minecraft.server.command.ServerCommandSource
import java.io.InputStream

interface IOnedriveUtils {
    fun initializeGraphForUserAuth(source: ServerCommandSource?, login: Boolean = false)

    suspend fun uploadOneDrive(service: BackupDatabaseService, id: Int)
    fun downloadBlob(hash: String): InputStream
}
