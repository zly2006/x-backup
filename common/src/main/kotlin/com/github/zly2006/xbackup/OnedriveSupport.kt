package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.api.CloudStorageProvider
import com.github.zly2006.xbackup.api.XBackupKotlinAsyncApi
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlin.random.Random
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
        // if get token failed, it will throw exception
        getOneDriveToken()

        service.activeTask = "Uploading to OneDrive"
        val tasks = backup.entries.filter {
            !it.isDirectory && it.cloudDriveId == null && it.getInputStream(service) != null
        }
        var done = 0
        val semaphore = Semaphore(4) // Limit to 4 concurrent uploads
        try {
            tasks.map { entry ->
                GlobalScope.async(Dispatchers.Default.limitedParallelism(4, "X-Backup-Uploader")) {
                    semaphore.withPermit {
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
                                else if (response.status.value == 429) {
                                    delay(Random.nextLong(5000, 15000))
                                }
                                else {
                                    throw IllegalStateException("Upload failed $entry: ${response.status} ${response.bodyAsText()}")
                                }
                            }
                            httpClient.put("https://graph.microsoft.com/v1.0/me/drive/root:$prefix/${entry.hash}:/content") {
                                header("Authorization", "Bearer $token")
                                header("Content-Type", "application/octet-stream")
                                header("Content-Length", entry.zippedSize.toString())
                                setBody(service.getBlobFile(entry.hash).readBytes())
                            }.let { response ->
                                if (!response.status.isSuccess()) {
                                    throw IllegalStateException("Upload failed $entry: ${response.status} ${response.bodyAsText()}")
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
                if (!response.status.isSuccess()) {
                    if (response.status.value == 404) {
                        throw DontRetryException(IllegalStateException("404: $hash, ${response.bodyAsText()}, ${response.request.url}"))
                    }
                }
                log.info("Downloading $hash, size: ${response.body<JsonObject>()["size"]?.jsonPrimitive?.long}")
            }
            httpClient.get("https://graph.microsoft.com/v1.0/me/drive/root:$prefix/$hash:/content") {
                header("Authorization", "Bearer $token")
            }.let { response ->
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Download failed: ${response.status} ${response.bodyAsText()}")
                }
                response.readBytes().inputStream()
            }
        }
    }
}
