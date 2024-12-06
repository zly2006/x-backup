package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.api.CloudStorageProvider
import com.github.zly2006.xbackup.api.XBackupKotlinAsyncApi
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.cio.readChannel
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlin.random.Random
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.update
import java.io.InputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

private const val prefix = "/Apps/xb.2"

class OnedriveSupport(
    private val config: Config,
    private val httpClient: HttpClient
): CloudStorageProvider {
    private var tokenExpires = 0L
    private var token = ""
    private val receivedBytes = atomic(0L)
    private val sentBytes = atomic(0L)
    override var bytesReceivedLastSecond = 0L
    override var bytesSentLastSecond = 0L
    private val job = GlobalScope.launch {
        while (true) {
            delay(1000)
            bytesReceivedLastSecond = receivedBytes.getAndSet(0)
            bytesSentLastSecond = sentBytes.getAndSet(0)
        }
    }

    init {
        job.invokeOnCompletion { cause -> log.info("Network stats tracker stopped", cause) }
    }

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
        val semaphore = Semaphore(4) // Limit to 4 concurrent uploads
        val file = Path(".tmp", "xb.upload.zip").createParentDirectories()
        service as BackupDatabaseService
        ZipOutputStream(file.outputStream()).use { stream ->
            service.zipArchive(stream, service.getBackup(id)!!)
        }
        // multi thread uploading, using semaphore to limit the number of concurrent uploads
        val fileSize = file.fileSize()
        val STEP = 10 * 1024 * 1024L
        // get item-id
        val item = httpClient.get("https://graph.microsoft.com/v1.0/me/drive/root:$prefix") {
            header("Authorization", "Bearer $token")
        }.body<JsonObject>()
        if (item["id"] == null) {
            throw IllegalStateException("Failed to get item-id")
        }
        val folderId = item["id"]!!.jsonPrimitive.content
        log.info("Folder id: $folderId")
        val uploadSession = httpClient.post("https://graph.microsoft.com/v1.0/me/drive/items/$folderId/createUploadSession") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    putJsonObject("item") {
                        put("@odata.type", "microsoft.graph.driveItemUploadableProperties")
                        put("@microsoft.graph.conflictBehavior", "rename")
                        put("name", "xb.upload.backup-${backup.id}.zip")
                    }
                }
            )
        }.let { response ->
            println(response.status)
            response.body<JsonObject>()
        }
        println(uploadSession)
        val tasks = (0 until fileSize step STEP).map { start ->
            GlobalScope.async(Dispatchers.IO) {
                semaphore.withPermit {
                    val end = minOf(start + STEP, fileSize)
                    val part = file.readChannel(start, end)
                }
            }
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

                var lastReceived = 0L
                onDownload { bytesSentTotal, contentLength ->
                    receivedBytes.addAndGet(bytesSentTotal - lastReceived)
                    lastReceived = bytesSentTotal
                }
            }.let { response ->
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Download failed: ${response.status} ${response.bodyAsText()}")
                }
                response.readBytes().inputStream()
            }
        }
    }
}
