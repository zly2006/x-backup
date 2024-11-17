package com.github.zly2006.xbackup

import java.io.InputStream

interface IOnedriveUtils {
    suspend fun uploadOneDrive(service: BackupDatabaseService, id: Int)

    suspend fun downloadBlob(hash: String): InputStream
}
