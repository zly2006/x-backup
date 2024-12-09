package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.api.CloudStorageProvider
import com.github.zly2006.xbackup.api.XBackupKotlinAsyncApi
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.update
import java.io.InputStream
import java.net.URI
import java.net.http.HttpRequest
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

class OnedriveSupport(
    private val config: Config,
    private val httpClient: HttpClient
): CloudStorageProvider {
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

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun uploadBackup(service: XBackupKotlinAsyncApi, id: Int) {
        val backup = requireNotNull(service.getBackup(id)) {
            "Backup not found"
        }
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
        val uploadSession = httpClient.post("https://redenmc.com/api/backup/v1/onedrive/upload") {
            header("Authorization", "Bearer ${config.cloudBackupToken}")
        }.body<JsonObject>()
        val javaNetClient = java.net.http.HttpClient.newBuilder()
            .executor(Dispatchers.IO.asExecutor())
            .build()
        var uploadJo: JsonObject? = null
        (0 until fileSize step STEP).map { start ->
            val endInclusive = minOf(start + STEP, fileSize) - 1
            val uploadUrl = uploadSession["uploadUrl"]!!.jsonPrimitive.content
            val part = file.readChannel(start, endInclusive).toByteArray()
            println("Uploading part: $start-$endInclusive")
            val res = javaNetClient.sendAsync(
                HttpRequest.newBuilder(URI(uploadUrl)).apply {
                    PUT(HttpRequest.BodyPublishers.ofByteArray(part))
                    header("Content-Range", "bytes $start-${endInclusive}/$fileSize")
                }.build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()
            ).asDeferred().await()
            require(res.statusCode() in 200..299) {
                "Failed to upload part: ${res.statusCode()}: ${res.body()}"
            }
            if (res.statusCode() == 201) {
                uploadJo = Json.decodeFromString<JsonObject>(res.body())
            }
            sentBytes.addAndGet(part.size.toLong())
            service.activeTaskProgress += (100 * part.size / fileSize).toInt()
        }
        uploadJo?.let { jojo ->
            service.syncDbQuery {
                BackupDatabaseService.BackupTable.update({
                    BackupDatabaseService.BackupTable.id eq id
                }) {
                    val url = "https://redenmc.com/api/backup/v1/onedrive/" + jojo["id"]!!.jsonPrimitive.content
                    it[cloudBackupUrl] = url
                    log.info("Upload complete: $url")
                }
            }
        }
        Path(".tmp", "xb.upload.zip").deleteIfExists()
    }

    override suspend fun downloadBlob(hash: String): InputStream {
        TODO()
    }
}
