package com.github.zly2006.xbackup.api

import java.io.InputStream

interface CloudStorageProvider {
    suspend fun uploadBackup(service: XBackupKotlinAsyncApi, id: Int)

    suspend fun downloadBlob(hash: String): InputStream
}
