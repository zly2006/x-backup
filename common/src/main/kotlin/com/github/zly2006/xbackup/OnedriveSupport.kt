package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.api.CloudStorageProvider
import com.github.zly2006.xbackup.api.XBackupKotlinAsyncApi
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.update
import java.io.InputStream
import kotlin.io.path.readBytes

private const val prefix = "/Apps/xb.2"

class OnedriveSupport(
    private val config: Config,
    private val httpClient: HttpClient
): CloudStorageProvider {
    private var tokenExpires = 0L
    private var token = ""

    private suspend fun getOneDriveToken(): String {
        if (tokenExpires < System.currentTimeMillis()) {
            val response = httpClient.get("https://redenmc.com/api/backup/onedrive") {
                header("Authorization", "Bearer ${config.cloudBackupToken}")
            }.bodyAsText()
            tokenExpires = System.currentTimeMillis() + 60_000
            token = response
            return response
        }
        return token
    }

    @OptIn(DelicateCoroutinesApi::class)
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
                        httpClient.get("https://graph.microsoft.com/v1.0/me/drive/root:$prefix/${entry.hash}") {
                            header("Authorization", "Bearer $token")
                        }.let { response ->
                            if (response.status.isSuccess()) {
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
                            else if (response.status.value == 404) {
                                log.info("Uploading $entry")
                            }
                            else {
                                throw IllegalStateException("Upload failed $entry: ${response.bodyAsText()}")
                            }
                        }
                        httpClient.put("https://graph.microsoft.com/v1.0/me/drive/root:$prefix/${entry.hash}:/content") {
                            header("Authorization", "Bearer $token")
                            header("Content-Type", "application/octet-stream")
                            header("Content-Length", entry.zippedSize.toString())
                            setBody(service.getBlobFile(entry.hash).readBytes())
                        }.let { response ->
                            if (!response.status.isSuccess()) {
                                throw IllegalStateException("Upload failed $entry: ${response.bodyAsText()}")
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
                        httpClient.get("https://graph.microsoft.com/v1.0/me/drive/root:$prefix/${entry.hash}") {
                            header("Authorization", "Bearer $token")
                        }.let { response ->
                            if (!response.status.isSuccess()) {
                                error("Upload failed $entry: ${response.bodyAsText()}")
                            }
                            val cloudDriveSize = response.body<JsonObject>()["size"]?.jsonPrimitive?.long
                            require(cloudDriveSize == entry.zippedSize) {
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

            httpClient.get("https://graph.microsoft.com/v1.0/me/drive/root:$prefix/$hash") {
                header("Authorization", "Bearer $token")
            }.let { response ->
                log.info("Downloading $hash, size: ${response.body<JsonObject>()["size"]?.jsonPrimitive?.long}")
            }
            httpClient.get("https://graph.microsoft.com/v1.0/me/drive/root:$prefix/$hash:/content") {
                header("Authorization", "Bearer $token")
            }.let { response ->
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Download failed: ${response.bodyAsText()}")
                }
                response.readBytes().inputStream()
            }
        }
    }
}
