package com.github.zly2006.xbackup

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.use
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.*

@Suppress("SuspendFunctionOnCoroutineScope")
class BackupDatabaseService(
    val database: Database,
    private val blobDir: Path,
    config: Config
) : CoroutineScope {
    private val log = LoggerFactory.getLogger("XBackup")!!
    @OptIn(DelicateCoroutinesApi::class)
    private val syncExecutor = newFixedThreadPoolContext(1, "XBackup-Sync")
    init {
        require(blobDir.isAbsolute && blobDir == blobDir.normalize()) {
            "Blob directory must be absolute and normalized"
        }
        if (!blobDir.isDirectory()) {
            log.warn("Blob directory not found, creating...")
        }

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                BackupEntryTable,
                BackupTable,
                BackupEntryBackupTable,
                withLogs = false
            )
        }
    }

    val oneDriveService: IOnedriveUtils by lazy {
        ServiceLoader.load(IOnedriveUtils::class.java)
            ?.firstOrNull() ?: error("Onedrive service not found")
    }
    
    private val ignoredFiles = setOf(
        "", // empty string is the root directory
        "x_backup.db.back",
        "x_backup.db",
        "x_backup.db-wal",
        "x_backup.db-shm",
        "x_backup.db-journal",
    ) + config.ignoredFiles

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    object BackupEntryTable : IntIdTable("backup_entries") {
        val path = varchar("path", 255).index()
        val size = long("size")
        val zippedSize = long("zipped_size")
        val lastModified = long("last_modified")
        val isDirectory = bool("is_directory")
        val hash = varchar("hash", 255).index()
        @Deprecated("compress = 1 is gzip")
        @ScheduledForRemoval
        val gzip = bool("gzip")

        /**
         * 0: no compress
         * 1: gzip
         * 2: zip (pack multiple files)
         */
        val compress = byte("compress").default(0)
        val cloudDriveId = byte("cloud_drive_id").default(0)
    }

    object BackupTable : IntIdTable("backups") {
        val size = long("size")
        val zippedSize = long("zipped_size")
        val created = long("created")
        val comment = varchar("comment", 255)
        val temporary = bool("temporary").default(false)
        val metadata = json<JsonObject>("metadata", Json).nullable()
    }

    object BackupEntryBackupTable : IntIdTable("backup_entry_backup") {
        val backup = reference("backup", BackupTable, ReferenceOption.CASCADE).index()
        val entry = reference("entry", BackupEntryTable, ReferenceOption.CASCADE).index()
    }

    @Serializable
    data class BackupEntry(
        val id: Int,
        val path: String,
        val size: Long,
        val zippedSize: Long,
        val lastModified: Long,
        val isDirectory: Boolean,
        val hash: String,
        val compress: Int,
        val cloudDriveId: Byte?,
    ) {
        override fun toString(): String {
            return "$id:/$path"
        }

        fun valid(service: BackupDatabaseService): Boolean {
            return isDirectory ||
                    (service.getBlobFile(hash).exists() && (service.getBlobFile(hash).fileSize() == zippedSize))
        }

        suspend fun getInputStream(service: BackupDatabaseService): InputStream? {
            val blob = service.getBlobFile(hash)
            if (cloudDriveId != null && !blob.exists()) {
                Class.forName("com.github.zly2006.xbackup.OnedriveUtils")
                service.oneDriveService.downloadBlob(hash)
            }
            if (!blob.exists()) {
                return null
            }
            return when (compress) {
                0 -> blob.inputStream()
                1 -> GZIPInputStream(blob.inputStream())
                2 -> ZipInputStream(blob.inputStream()).use {
                    @Suppress("ControlFlowWithEmptyBody")
                    while (it.nextEntry.let { zipEntry ->
                            if (zipEntry == null) false
                            else zipEntry.name != path
                        }) { }
                    it
                }

                else -> error("Unknown compress type: $compress")
            }
        }
    }

    @Serializable
    class Backup(
        val id: Int,
        val size: Long,
        val zippedSize: Long,
        val created: Long,
        val comment: String,
        val entries: List<BackupEntry>,
        val temporary: Boolean,
        val metadata: JsonObject?,
    )

    data class XBackupStatus(
        val blobDiskUsage: Long,
        val actualUsage: Long,
        val backupCount: Long,
        val latestBackup: Backup?,
    )

    data class BackupResult(
        val success: Boolean,
        val message: String,
        val backId: Int,
        val totalSize: Long,
        val compressedSize: Long,
        val addedSize: Long,
        val millis: Long
    )

    suspend fun status(): XBackupStatus {
        val blobDiskUsage = blobDir.toFile().walk().filter { it.isFile }.sumOf { it.length() }
        val actualUsage = dbQuery {
            BackupEntryTable.select(BackupEntryTable.zippedSize.sum())
                .firstOrNull()?.get(BackupEntryTable.zippedSize.sum()) ?: 0L
        }
        val backupCount = dbQuery { BackupTable.selectAll().count() }
        val latestBackup = getLatestBackup()
        return XBackupStatus(blobDiskUsage, actualUsage, backupCount, latestBackup)
    }

    suspend fun createBackup(
        root: Path,
        comment: String,
        temporary: Boolean = false,
        metadata: JsonObject? = null,
        predicate: (Path) -> Boolean,
    ): BackupResult {
        if (blobDir.startsWith(root.absolute().normalize())) {
            error("Blob directory cannot be inside the backup directory")
        }
        val files = ConcurrentHashMap.newKeySet<String>()
        val timeStart = System.currentTimeMillis()

        val newEntries = ConcurrentHashMap.newKeySet<BackupEntry>()
        val entries = root.normalize().toFile().walk().filter {
            it.name !in ignoredFiles && predicate(it.toPath())
        }.map { sourceFile ->
            @Suppress("SuspendFunctionOnCoroutineScope")
            this.async(Dispatchers.IO) {
                retry(5) {
                    try {
                        val path = root.normalize().relativize(sourceFile.toPath()).normalize()
                        files.add(path.toString())
                        val existing = dbQuery {
                            BackupEntryTable.selectAll().where {
                                var exp = BackupEntryTable.path eq path.toString() and
                                        (BackupEntryTable.isDirectory eq sourceFile.isDirectory)
                                if (sourceFile.isFile) {
                                    exp = exp and (BackupEntryTable.size eq sourceFile.length()) and
                                            (BackupEntryTable.lastModified eq sourceFile.lastModified())
                                }
                                exp
                            }.map { it.toBackupEntry() }.firstOrNull {
                                it.valid(this@BackupDatabaseService)
                            }
                        }
                        if (existing != null) {
                            if (sourceFile.isDirectory) {
                                return@retry existing
                            }
                            else if (sourceFile.isFile) {
                                if (getBlobFile(existing.hash).exists()) {
                                    return@retry existing
                                }
                            }
                        }
                        val md5 = if (sourceFile.isFile) {
                            sourceFile.inputStream().use { input ->
                                val digest = MessageDigest.getInstance("MD5")
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (input.read(buffer).also { read = it } > 0) {
                                    digest.update(buffer, 0, read)
                                }
                                digest.digest()
                            }.joinToString("") { "%02x".format(it) }
                        }
                        else ""
                        dbQuery {
                            BackupEntryTable.selectAll().where {
                                BackupEntryTable.path eq path.toString() and
                                        (BackupEntryTable.isDirectory eq sourceFile.isDirectory) and
                                        (BackupEntryTable.hash eq md5)
                            }.map { it.toBackupEntry() }.firstOrNull {
                                it.valid(this@BackupDatabaseService)
                            }
                        }?.let { return@retry it }

                        val blob = getBlobFile(md5)
                        val gzip = sourceFile.length() > 1024
                        val zippedSize: Long
                        if (sourceFile.isFile) {
                            if (!blob.exists()) runCatching {
                                // fuck u mcos
                                blob.createParentDirectories().createFile()
                            }
                            if (!gzip) {
                                try {
                                    blob.outputStream().buffered().use { output ->
                                        sourceFile.inputStream().buffered().use { input ->
                                            input.copyTo(output)
                                        }
                                    }
                                } catch (_: FileAlreadyExistsException) {
                                    // fuck u macos
                                }
                                zippedSize = sourceFile.length()
                            }
                            else {
                                GZIPOutputStream(blob.outputStream().buffered()).use { stream ->
                                    sourceFile.inputStream().buffered().use { input ->
                                        input.copyTo(stream)
                                    }
                                }
                                zippedSize = blob.fileSize()
                            }
                        }
                        else {
                            zippedSize = 0
                        }
                        if (sourceFile.isFile) {
                            if (MessageDigest.getInstance("MD5").digest(sourceFile.readBytes())
                                    .joinToString("") { "%02x".format(it) } != md5
                            ) {
                                error("File hash mismatch when creating backup, file: $path, expected: $md5")
                            }
                        }
                        withContext(syncExecutor) {
                            dbQuery {
                                val backupEntry = BackupEntryTable.insert {
                                    it[this.path] = path.toString()
                                    it[this.size] = sourceFile.length()
                                    it[this.lastModified] = sourceFile.lastModified()
                                    it[this.isDirectory] = sourceFile.isDirectory
                                    it[this.hash] = md5
                                    it[this.zippedSize] = zippedSize
                                    it[this.gzip] = false
                                    it[this.compress] = if (gzip) 1 else 0
                                }.resultedValues!!.single().toBackupEntry()
                                newEntries.add(backupEntry)
                                backupEntry
                            }
                        }
                    } catch (e: IOException) {
                        throw IOException("Error backing up file: $sourceFile", e)
                    }
                }
            }
        }.toList().awaitAll()
        require(files.size == entries.size)
        Path("debug-backup.json").writeText(Json.encodeToString(files.toList()))
        log.info("[X Backup] Backed up ${entries.size} files, ${newEntries.size} new, ${entries.size - newEntries.size} files reused")
        val backup = dbQuery {
            val backup = BackupTable.insert {
                it[size] = entries.sumOf { it.size }
                it[zippedSize] = entries.sumOf { it.zippedSize }
                it[created] = System.currentTimeMillis()
                it[this.comment] = comment
                it[this.temporary] = temporary
                it[this.metadata] = metadata
            }.resultedValues!!.single().toBackup()
            entries.forEach { entry ->
                BackupEntryBackupTable.insert {
                    it[this.backup] = backup.id
                    it[this.entry] = entry.id
                }
            }
            // recheck
            val entryList = backup.entries.filter {
                !it.isDirectory &&
                        (!getBlobFile(it.hash).exists() || getBlobFile(it.hash).fileSize() != it.zippedSize)
            }
            if (entryList.isNotEmpty()) {
                log.error(entryList.toString())
                error("Backup failed, ${entryList.size} files not backed up")
            }
            backup
        }
        return BackupResult(
            true,
            "OK",
            backup.id,
            backup.size,
            backup.zippedSize,
            newEntries.sumOf { it.zippedSize },
            System.currentTimeMillis() - timeStart,
        )
    }

    private suspend inline fun <T> retry(times: Int, function: () -> T): T {
        var lastException: Throwable? = null
        repeat(times) {
            try {
                return function()
            } catch (e: Throwable) {
                log.error("Error in retry, attempt ${it + 1}/$times", e)
                lastException = e
                delay(1000)
            }
        }
        throw RuntimeException("Retry failed", lastException)
    }

    suspend fun deleteBackup(backup: Backup) {
        dbQuery {
            backup.entries.forEach { entry ->
                if (BackupEntryBackupTable.selectAll().where {
                        BackupEntryBackupTable.entry eq entry.id and
                                (BackupEntryBackupTable.backup neq backup.id)
                    }.empty()
                ) {
                    getBlobFile(entry.hash).toFile().delete()
                    BackupEntryTable.deleteWhere {
                        BackupEntryTable.id eq entry.id
                    }
                }
            }
            BackupTable.deleteWhere { BackupTable.id eq backup.id }
        }
    }

    fun getBlobFile(hash: String): Path {
        return blobDir.resolve(hash.take(2)).resolve(hash.drop(2)).createParentDirectories()
    }

    suspend fun getBackup(id: Int): Backup? = dbQuery {
        BackupTable.selectAll().where { BackupTable.id eq id }.firstOrNull()?.toBackup()
    }

    /**
     * Restore backup to target directory
     *
     * @param id Backup ID
     * @param target Target directory
     * @param ignored Predicate to ignore files, this prevents files from being deleted,
     * usually should be opposite of the predicate used in [createBackup]
     */
    suspend fun restore(id: Int, target: Path, ignored: (Path) -> Boolean) = dbQuery {
        val backup = getBackup(id) ?: error("Backup not found")
        val map = backup.entries.associateBy { it.path }.filter { !ignored(Path(it.key)) }
        for (it in target.normalize().toFile().walk().drop(1)) {
            val path = target.normalize().relativize(it.toPath()).normalize()
            if (it.name in ignoredFiles || ignored(path))
                continue
            val entry = map[path.toString()]
            if (entry == null && it.isFile) {
                log.info("[X Backup] Deleting $path, not found in backup")
                it.delete()
            }
            if (entry != null && entry.isDirectory != it.isDirectory) {
                log.info("[X Backup] Deleting $path, directory mismatch")
                it.deleteRecursively()
            }
        }
        val done = atomic(0)
        val verbose = map.size < 100
        log.info("[X Backup] ${map.size} files to restore")
        Path("debug-restore.json").writeText(Json.encodeToString(map.keys.toList()))
        val deferredList = map.map {
            this@BackupDatabaseService.async {
                var worked = false
                val path = target.resolve(it.key).normalize().createParentDirectories()
                if (it.value.lastModified != path.toFile().lastModified() || it.value.size != path.fileSize()) {
                    retry(5) {
                        if (it.value.isDirectory) {
                            path.toFile().mkdirs()
                            path.toFile().setLastModified(it.value.lastModified)
                        }
                        else {
                            if (!path.exists()) {
                                path.createParentDirectories().createFile()
                            }
                            val blob = getBlobFile(it.value.hash)
                            path.outputStream().buffered().use { output ->
                                val input = it.value.getInputStream(this@BackupDatabaseService)
                                if (input == null) {
                                    log.error("Blob not found for file ${it.key}, hash: ${it.value.hash}")
                                    return@async
                                }
                                // copy
                                input.use {
                                    it.copyTo(output)
                                }
                            }
                            val checkAgain =
                                MessageDigest.getInstance("MD5").digest(path.toFile().inputStream().readBytes())
                                    .joinToString("") { "%02x".format(it) }
                            if (checkAgain != it.value.hash) {
                                val bytes = GZIPInputStream(blob.toFile().inputStream().buffered()).readBytes()
                                val gzipMd5 = MessageDigest.getInstance("MD5").digest(bytes)
                                    .joinToString("") { "%02x".format(it) }
                                log.error(
                                    "File hash mismatch, file: $path, expected: ${it.value.hash}, actual: $checkAgain, gzip: $gzipMd5" +
                                            if (it.value.hash == gzipMd5 && gzipMd5 != checkAgain) " (writing file failed?)"
                                            else if (it.value.hash != gzipMd5 && gzipMd5 == checkAgain) " (bad md5 when creating backup?)"
                                            else " (WTF???)"
                                )
                                path.writeBytes(bytes)
                            }
                            require(path.fileSize() == it.value.size)
                            path.toFile().setLastModified(it.value.lastModified)
                            worked = true
                        }
                    }
                }
                val doneNow = done.incrementAndGet()
                if (verbose || doneNow % 30 == 0 && worked) {
                    log.info("[X Backup] Restored $done files // current: ${it.key}")
                }
            }
        }
        val reportJob = this@BackupDatabaseService.launch {
            while (done.value < map.size) {
                delay(5000)
                log.info("[X Backup] Restored ${done.value}/${map.size} files")
            }
        }
        deferredList.awaitAll()
        reportJob.cancelAndJoin()
        log.info("Restored backup $id")
    }

    /**
     * Check if this backup is valid
     */
    fun check(backup: Backup): Boolean {
        var valid = true
        backup.entries.forEach {
            val blobFile = getBlobFile(it.hash)
            if (it.isDirectory) return@forEach
            if (it.cloudDriveId != null) {

            }
            else if (!blobFile.exists()) {
                log.error("Blob not found for file ${it.path}, hash: ${it.hash}")
                valid = false
            }
            else if (blobFile.fileSize() != it.zippedSize) {
                log.error("Blob size mismatch for file ${it.path}, expected: ${it.zippedSize}, actual: ${blobFile.fileSize()}")
                valid = false
            }
        }
        return valid
    }

    private suspend fun packFiles(blobs: List<BackupEntry>): String {
        val stream = ByteArrayOutputStream()
        ZipOutputStream(stream).use { zip ->
            zip.setLevel(9)
            zip.setComment("X-Backup")

            blobs.forEach {
                require(it.compress != 2) { "already packed" }
                val entry = zip.putNextEntry(
                    ZipEntry(it.path.replace('\\', '/').trim('/')).apply {
                        creationTime = FileTime.fromMillis(it.lastModified)
                        lastModifiedTime = FileTime.fromMillis(it.lastModified)
                        comment = buildJsonObject {
                            put("hash", it.hash)
                            put("cloud", it.cloudDriveId)
                        }.toString()
                    })
                val input = requireNotNull(it.getInputStream(this)) {
                    "Blob not found for file ${it.path}, hash: ${it.hash}"
                }
                input.copyTo(zip)
                input.close()
            }
        }
        val md5 = MessageDigest.getInstance("MD5").digest(stream.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = getBlobFile(md5)
        if (!file.exists()) {
            file.createParentDirectories().createFile()
            withContext(Dispatchers.IO) {
                stream.writeTo(file.outputStream())
            }
        }
        return md5
    }

    suspend fun packBackup(backup: Backup) {
        dbQuery {
            val entryList = backup.entries.filter {
                !it.isDirectory && it.size < 1024 * 1024 * 50 // 50MB
            }
            if (entryList.size < 10) return@dbQuery
            val packed = packFiles(entryList)
            val blobSize = getBlobFile(packed).fileSize()
            entryList.forEach {
                dbQuery {
                    BackupEntryTable.update({ BackupEntryTable.id eq it.id }) {
                        it[compress] = 2
                        it[cloudDriveId] = 0
                        it[hash] = packed
                        it[zippedSize] = blobSize
                    }
                }
            }
        }
    }

    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database, statement = block)

    fun listBackups(offset: Int, limit: Int): List<Backup> {
        return transaction {
            BackupTable.selectAll()
                .orderBy(BackupTable.id to SortOrder.DESC)
                .limit(limit, offset.toLong()).toList()
                .map { it.toBackup() }
        }
    }

    suspend fun getLatestBackup(): Backup? = dbQuery {
        BackupTable.selectAll().lastOrNull()?.toBackup()
    }

    fun backupCount() = transaction {
        BackupTable.selectAll().count().toInt()
    }

    companion object {
        private fun ResultRow.toBackup(): Backup {
            val id = this[BackupTable.id].value
            val entries = BackupEntryBackupTable.select(BackupEntryBackupTable.entry).where {
                BackupEntryBackupTable.backup eq id
            }
            return Backup(
                id,
                this[BackupTable.size],
                this[BackupTable.zippedSize],
                this[BackupTable.created],
                this[BackupTable.comment],
                BackupEntryTable.selectAll().where {
                    BackupEntryTable.id inSubQuery entries
                }.map { it.toBackupEntry() },
                this[BackupTable.temporary],
                this[BackupTable.metadata]
            )
        }

        private fun ResultRow.toBackupEntry() = BackupEntry(
            this[BackupEntryTable.id].value,
            this[BackupEntryTable.path],
            this[BackupEntryTable.size],
            this[BackupEntryTable.zippedSize],
            this[BackupEntryTable.lastModified],
            this[BackupEntryTable.isDirectory],
            this[BackupEntryTable.hash],
            if (this[BackupEntryTable.gzip]) {
                BackupEntryTable.update({
                    BackupEntryTable.id eq this@toBackupEntry[BackupEntryTable.id]
                }) {
                    it[compress] = 1
                    it[gzip] = false
                }
                1
            }
            else this[BackupEntryTable.compress].toInt(),
            this[BackupEntryTable.cloudDriveId].let { if (it == 0.toByte()) null else it },
        )
    }
}
