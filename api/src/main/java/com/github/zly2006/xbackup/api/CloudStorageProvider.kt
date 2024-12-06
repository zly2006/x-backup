package com.github.zly2006.xbackup.api

import java.io.InputStream

interface CloudStorageProvider {
    val bytesSentLastSecond: Long get() = 0
    val bytesReceivedLastSecond: Long get() = 0

    suspend fun uploadBackup(service: XBackupKotlinAsyncApi, id: Int)

    suspend fun downloadBlob(hash: String): InputStream
}
