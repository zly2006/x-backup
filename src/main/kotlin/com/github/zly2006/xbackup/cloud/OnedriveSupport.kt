package com.github.zly2006.xbackup.cloud

import com.github.zly2006.xbackup.BackupDatabaseService
import com.github.zly2006.xbackup.Config
import com.github.zly2006.xbackup.api.CloudStorageProvider
import com.github.zly2006.xbackup.api.XBackupKotlinAsyncApi
import com.github.zly2006.xbackup.retry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.cio.readChannel
import io.ktor.util.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.future.asDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.ZipOutputStream
import kotlin.apply
import kotlin.collections.map
import kotlin.getOrThrow
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.use
import kotlin.let
import kotlin.ranges.step
import kotlin.ranges.until
import kotlin.runCatching
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val STEP = 10 * 1024 * 1024L

class OnedriveSupport(
    private val config: Config,
    private val httpClient: HttpClient
): CloudStorageProvider {
    private val log = LoggerFactory.getLogger("X Backup/OnrDrive")!!
    override var bytesReceivedLastSecond = 0L
    override var bytesSentLastSecond = 0L
    private var uploadTask: Deferred<Result<Unit>>? = null

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun uploadBackup(service: XBackupKotlinAsyncApi, id: Int) {
        try {
            uploadTask?.cancelAndJoin()
        } catch (_: CancellationException) {
        }
        uploadTask = GlobalScope.async {
            runCatching {
                val backup = requireNotNull(service.getBackup(id)) { "Backup not found" }
                service.activeTask = "Uploading to OneDrive"
                val file = Path(".tmp", "xb.upload.zip").createParentDirectories()
                service as BackupDatabaseService
                ZipOutputStream(file.outputStream()).use { stream ->
                    service.zipArchive(stream, service.getBackup(id)!!)
                }
                val fileSize = file.fileSize()
                log.info("Zip file size: ${fileSize / 1024 / 1024}MB")
                // get item-id
                val uploadSession = retry(5) {
                    httpClient.post("https://redenmc.com/api/backup/v1/onedrive/upload") {
                        header("Authorization", "Bearer ${config.cloudBackupToken}")
                        contentType(ContentType.Application.Json)
                        setBody("backup")
                    }.body<JsonObject>()
                }
                log.info("Received upload session from server")
                val javaNetClient = java.net.http.HttpClient.newBuilder()
                    .executor(Dispatchers.IO.asExecutor())
                    .build()
                var uploadJo: JsonObject? = null
                (0 until fileSize step STEP).map { start ->
                    retry(3) {
                        val endInclusive = kotlin.comparisons.minOf(start + STEP, fileSize) - 1
                        val uploadUrl = uploadSession["uploadUrl"]!!.jsonPrimitive.content
                        val part = file.readChannel(start, endInclusive).toByteArray()
                        log.info("Uploading part: $start-$endInclusive")
                        val timeStart = System.currentTimeMillis()
                        val res = javaNetClient.sendAsync(
                            HttpRequest.newBuilder(URI(uploadUrl)).apply {
                                PUT(HttpRequest.BodyPublishers.ofByteArray(part))
                                header("Content-Range", "bytes $start-${endInclusive}/$fileSize")
                            }.build(),
                            HttpResponse.BodyHandlers.ofString()
                        ).asDeferred().await()
                        require(res.statusCode() in 200..299) {
                            "Failed to upload part: ${res.statusCode()}: ${res.body()}"
                        }
                        if (res.statusCode() == 201) {
                            uploadJo = Json.decodeFromString<JsonObject>(res.body())
                        }
                        val timeEnd = System.currentTimeMillis()
                        bytesSentLastSecond = (part.size * 1000 / (timeEnd - timeStart))
                        service.activeTaskProgress += (100 * part.size / fileSize).toInt()
                    }
                }
                uploadJo?.let { jojo ->
                    val itemId = jojo["id"]!!.jsonPrimitive.content
                    service.syncDbQuery {
                        BackupDatabaseService.BackupTable.update({
                            BackupDatabaseService.BackupTable.id eq id
                        }) {
                            val url = "https://redenmc.com/api/backup/v1/onedrive/$itemId"
                            it[cloudBackupUrl] = url
                            log.info("Upload complete: $url")
                        }
                    }
                    httpClient.post("https://redenmc.com/api/backup/v1/onedrive/$itemId") {
                        header("Authorization", "Bearer ${config.cloudBackupToken}")
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("localPath", service.databaseDir.parent.absolutePathString())
                            put("worldName", service.databaseDir.name)
                        }.toString())
                    }
                }
                Path(".tmp", "xb.upload.zip").deleteIfExists()
                uploadTask = null
            }
        }
        uploadTask?.await()?.getOrThrow()
    }
}
