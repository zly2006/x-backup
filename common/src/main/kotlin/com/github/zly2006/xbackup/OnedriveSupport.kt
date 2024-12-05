package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.api.CloudStorageProvider
import com.github.zly2006.xbackup.api.XBackupKotlinAsyncApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.jetbrains.exposed.sql.update
import java.io.InputStream

class OnedriveSupport(
    private val config: Config,
    private val httpClient: OkHttpClient
) : CloudStorageProvider {
    private var tokenExpires = 0L
    private var token = ""

    private fun getOneDriveToken(): String {
        if (tokenExpires < System.currentTimeMillis()) {
            val response = httpClient.newCall(
                Request.Builder().apply {
                    url("https://redenmc.com/api/backup/onedrive")
                    header("Authorization", "Bearer ${config.cloudBackupToken}")
                }.build()
            ).execute().body!!.string()
            tokenExpires = System.currentTimeMillis() + 60_000
            token = response
            return response
        }
        return token
    }

    override suspend fun uploadBackup(service: XBackupKotlinAsyncApi, id: Int) {
        val backup = requireNotNull(service.getBackup(id)) {
            "Backup not found"
        }

        service.activeTask = "Uploading to OneDrive"
        val tasks = backup.entries.filter {
            !it.isDirectory && it.cloudDriveId == null && it.getInputStream(service) != null
        }
        var done = 0
        try {
            tasks.map { entry ->
                GlobalScope.async {
                    retry(5) {
                        val token = getOneDriveToken()
                        httpClient.newCall(
                            Request.Builder().apply {
                                url("https://graph.microsoft.com/v1.0/me/drive/root:/Apps/xb/${entry.hash}")
                                header("Authorization", "Bearer $token")
                            }.build()
                        ).execute().use { response ->
                            if (response.isSuccessful) {
                                service.syncDbQuery {
                                    BackupDatabaseService.BackupEntryTable.update({
                                        BackupDatabaseService.BackupEntryTable.id eq entry.id
                                    }) {
                                        it[this.cloudDriveId] = 1
                                    }
                                }
                                log.info("File already exists in cloud: $entry")
                                done++
                                return@retry
                            }
                            else if (response.code == 404) {
                                log.info("Uploading $entry")
                            }
                            else {
                                throw IllegalStateException("Upload failed $entry: ${response.body!!.string()}")
                            }
                        }
                        httpClient.newCall(
                            Request.Builder().apply {
                                url("https://graph.microsoft.com/v1.0/me/drive/root:/Apps/xb/${entry.hash}:/content")
                                header("Authorization", "Bearer $token")
                                header("Content-Type", "application/octet-stream")
                                header("Content-Length", entry.zippedSize.toString())
                                put(entry.getInputStream(service)!!.readBytes().toRequestBody())
                            }.build()
                        ).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw IllegalStateException("Upload failed $entry: ${response.body!!.string()}")
                            }
                            service.syncDbQuery {
                                BackupDatabaseService.BackupEntryTable.update({
                                    BackupDatabaseService.BackupEntryTable.id eq entry.id
                                }) {
                                    it[this.cloudDriveId] = 1
                                }
                            }
                            log.info("Uploaded $entry")
                        }
                        httpClient.newCall(
                            Request.Builder().apply {
                                url("https://graph.microsoft.com/v1.0/me/drive/root:/Apps/xb/${entry.hash}")
                                header("Authorization", "Bearer $token")
                            }.build()
                        ).execute().use { response ->
                            if (!response.isSuccessful) {
                                error("Upload failed $entry: ${response.body!!.string()}")
                            }
                            val cloudDriveSize = response.body!!.json()["size"]?.jsonPrimitive?.long
                            require(cloudDriveSize == entry.zippedSize || cloudDriveSize == entry.size) {
                                "Uploaded size mismatch: $entry, expected: ${entry.zippedSize}, actual: $cloudDriveSize, raw: ${entry.size}"
                            }
                        }
                        done++
                        service.activeTaskProgress = done * 100 / tasks.size
                    }
                }
            }.awaitAll()
        } catch (e: Throwable) {
            log.error("Error in upload", e)
            service.activeTask = "Error: Upload to OneDrive failed"
            service.activeTaskProgress = -400
        }
    }

    override suspend fun downloadBlob(hash: String): InputStream {
        return retry(5) {
            val token = getOneDriveToken()

            httpClient.newCall(
                Request.Builder().apply {
                    url("https://graph.microsoft.com/v1.0/me/drive/root:/Apps/xb/$hash")
                    header("Authorization", "Bearer $token")
                }.build()
            ).execute().use { response ->
                log.info("Downloading $hash, size: ${response.body!!.json()["size"]?.jsonPrimitive?.long}")
            }
            httpClient.newCall(
                Request.Builder().apply {
                    url("https://graph.microsoft.com/v1.0/me/drive/root:/Apps/xb/$hash:/content")
                    header("Authorization", "Bearer $token")
                }.build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed: ${response.body!!.string()}")
                }
                response.body!!.bytes().inputStream()
            }
        }
    }
}

private fun ResponseBody.json() = Json.decodeFromString<JsonObject>(string())
