package com.github.zly2006.xbackup.cloud

import com.github.zly2006.xbackup.*
import com.github.zly2006.xbackup.Utils.broadcast
import com.github.zly2006.xbackup.api.CloudStorageProvider
import com.github.zly2006.xbackup.api.XBackupKotlinAsyncApi
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.cancel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.network.chat.ClickEvent
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

private const val STEP = 10 * 1024 * 1024L

fun <T, R> T.lazy(block: T.() -> R) = kotlin.lazy {
    block(this)
}

class OnedriveSupport(
    private val config: Config,
    private val httpClient: HttpClient,
) : CloudStorageProvider {
    private val log = LoggerFactory.getLogger("X Backup/OneDrive")!!
    override var bytesReceivedLastSecond = 0L
    override var bytesSentLastSecond = 0L
    private var uploadTask: Deferred<Result<Unit>>? = null

    @Serializable
    data class TempFileData(
        val backupId: Int,
        var compressedSize: Long,
        val uploadSession: JsonObject?,
        val uploadedParts: MutableList<Int>,
        val finished: Boolean,
    )

    @Serializable
    data class UploadRequest(
        val xbVersion: String,
        val worldName: String,
        val localPath: String,
        val comment: String,
        val zipSize: Long,
        val md5: String,
        val sha1: String,
    )

    fun getTempFileData(): TempFileData? {
        val file = Path(".tmp", "xb.upload.json")
        if (!file.exists()) return null
        return runCatching {
            Json.decodeFromString<TempFileData>(file.readText())
        }.getOrNull()
    }

    fun saveTempFileData(data: TempFileData) {
        val file = Path(".tmp", "xb.upload.json")
        file.createParentDirectories()
        file.writeText(Json.encodeToString(data))
    }

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
                val tempData = getTempFileData()?.takeIf { it.backupId == id }
                    ?: TempFileData(id, 0, null, mutableListOf(), false)
                service as BackupDatabaseService
                saveTempFileData(tempData)
                if (tempData.compressedSize == 0L) {
                    ZipOutputStream(file.outputStream()).use { stream ->
                        service.zipArchive(stream, backup)
                    }
                }
                val fileSize = file.fileSize()
                log.info("Zip file size: ${fileSize / 1024 / 1024}MB")
                tempData.compressedSize = fileSize
                saveTempFileData(tempData)
                val fileMd5 = file.inputStream().digest("MD5")
                val fileSha1 = file.inputStream().digest("SHA-1")
                // get item-id
                val uploadSession = tempData.uploadSession ?: retry(5) {
                    val response = httpClient.post("https://api.redenmc.com/api/backup/v1/onedrive/upload") {
                        header("Authorization", "Bearer ${config.cloudBackupToken}")
                        contentType(ContentType.Application.Json)
                        setBody(
                            UploadRequest(
                                XBackup.MOD_VERSION,
                                service.databaseDir.name,
                                service.databaseDir.parent.absolutePathString(),
                                backup.comment,
                                fileSize,
                                fileMd5,
                                fileSha1
                            )
                        )
                    }
                    require(response.status.isSuccess()) {
                        runCatching {
                            val jojo = Json.decodeFromString<JsonObject>(response.bodyAsText())
                            log.error("Failed to get upload session: $jojo")
                            val err = jojo["error"]?.jsonPrimitive?.content
                            if (err?.startsWith("freePlan:") == true) {
                                service.activeTaskProgress = -1
                                service.activeTask = "Failed to get upload session: Free plan limit"
                                 XBackup.server?.broadcast(Utils.translate("message.xb.error.free_plan_limit").withStyle {
                                     it.withClickEvent(
                                         ClickEvent.OpenUrl(URI("https://redenmc.com/x-backup/plans"))
                                     )
                                 })
                                throw DontRetryException(IllegalStateException("Free plan limit"))
                            }
                        }
                        "Failed to get upload session: ${response.status}"
                    }
                    log.info("Received upload session from reden api")
                    response.body<JsonObject>()
                }
                val javaNetClient = java.net.http.HttpClient.newBuilder()
                    .executor(Dispatchers.IO.asExecutor())
                    .build()
                var uploadJo: JsonObject? = null
                val startSlice = tempData.uploadedParts.maxOrNull() ?: 0
                (startSlice until fileSize step STEP).map { start ->
                    retry(10) {
                        if (getTempFileData()?.backupId != backup.id) {
                            throw DontRetryException(IllegalStateException("Backup changed"))
                        }
                        val endInclusive = kotlin.comparisons.minOf(start + STEP, fileSize) - 1
                        val uploadUrl = uploadSession["uploadUrl"]!!.jsonPrimitive.content
                        val channel = file.readChannel(start, endInclusive)
                        val part = channel.toByteArray()
                        log.info("Uploading part: $start-$endInclusive ${sizeToString(start)}")
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
                        tempData.uploadedParts.add((start / STEP).toInt())
                        saveTempFileData(tempData)
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
                        setBody(
                            UploadRequest(
                                XBackup.MOD_VERSION,
                                service.databaseDir.name,
                                service.databaseDir.parent.absolutePathString(),
                                backup.comment,
                                fileSize,
                                fileMd5,
                                fileSha1
                            )
                        )
                    }
                }
                Path(".tmp", "xb.upload.zip").deleteIfExists()
                Path(".tmp", "xb.upload.json").deleteIfExists()
                uploadTask = null
            }
        }
        uploadTask?.await()?.getOrThrow()
    }
}
