package com.github.zly2006.xbackup

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.*

@Suppress("SuspendFunctionOnCoroutineScope")
class BackupDatabaseService(
    val database: Database,
    private val blobDir: Path,
    private val config: Config
) : CoroutineScope {
    val log = LoggerFactory.getLogger("XBackup")!!

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

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                BackupEntryTable,
                BackupTable,
                BackupEntryBackupTable
            )
        }
    }

    object BackupEntryTable : IntIdTable("backup_entries") {
        val path = varchar("path", 255).index()
        val size = long("size")
        val zippedSize = long("zipped_size")
        val lastModified = long("last_modified")
        val isDirectory = bool("is_directory")
        val hash = varchar("hash", 255).index()
        val gzip = bool("gzip")
        val cloudDriveId = byte("cloud_drive_id").default(0)
    }

    object BackupTable : IntIdTable("backups") {
        val size = long("size")
        val zippedSize = long("zipped_size")
        val created = long("created")
        val comment = varchar("comment", 255)
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
        val gzip: Boolean,
        val cloudDriveId: Byte?,
    ) {
        override fun toString(): String {
            return "$id:/$path"
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
    )

    data class XBackupStatus(
        val blobDiskUsage: Long,
        val actualUsage: Long,
        val backupCount: Long,
        val latestBackup: Backup?,
    )

    suspend fun status() {
        val blobDiskUsage = blobDir.toFile().walk().filter { it.isFile }.sumOf { it.length() }
        val actualUsage = dbQuery { BackupEntryTable.selectAll().sumOf { it[BackupEntryTable.zippedSize] } }
        val backupCount = dbQuery { BackupTable.selectAll().count() }
        val latestBackup = getLatestBackup()
        XBackupStatus(blobDiskUsage, actualUsage, backupCount, latestBackup)
    }

    suspend fun createBackup(root: Path, comment: String, predicate: (Path) -> Boolean): BackupResult {
        if (blobDir.absolute().normalize().startsWith(root.absolute().normalize())) {
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
                            }.firstOrNull()?.toBackupEntry()
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
                                BackupEntryTable.hash eq md5
                            }.firstOrNull()?.toBackupEntry()
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

                        val backupEntry = BackupEntry(
                            id = -1,
                            path = path.toString(),
                            size = sourceFile.length(),
                            lastModified = sourceFile.lastModified(),
                            isDirectory = sourceFile.isDirectory,
                            hash = md5,
                            zippedSize = zippedSize,
                            gzip = gzip,
                            cloudDriveId = null,
                        )
                        if (sourceFile.isFile) {
                            if (MessageDigest.getInstance("MD5").digest(sourceFile.readBytes())
                                    .joinToString("") { "%02x".format(it) } != backupEntry.hash
                            ) {
                                error("File hash mismatch when creating backup, file: $path, expected: ${backupEntry.hash}")
                            }
                        }
                        newEntries.add(backupEntry)
                        backupEntry
                    } catch (e: IOException) {
                        throw IOException("Error backing up file: $sourceFile", e)
                    }
                }
            }
        }.toList().awaitAll().map { entry ->
            if (entry.id == -1) {
                // not yet in database
                dbQuery {
                    val id = BackupEntryTable.insertAndGetId {
                        it[path] = entry.path
                        it[size] = entry.size
                        it[zippedSize] = entry.zippedSize
                        it[lastModified] = entry.lastModified
                        it[isDirectory] = entry.isDirectory
                        it[hash] = entry.hash
                        it[gzip] = entry.gzip
                        it[cloudDriveId] = entry.cloudDriveId ?: 0
                    }
                    entry.copy(id = id.value)
                }
            }
            else entry
        }
        require(files.size == entries.size)
        Path("debug-backup.json").writeText(Json.encodeToString(files.toList()))
        log.info("[X Backup] Backed up ${entries.size} files, ${newEntries.size} new, ${entries.size - newEntries.size} files reused")
        val backup = dbQuery {
            val backup = BackupTable.insert {
                it[size] = entries.sumOf { it.size }
                it[zippedSize] = entries.sumOf { it.zippedSize }
                it[created] = System.currentTimeMillis()
                it[this.comment] = comment
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

    suspend fun deleteBackup(id: Int) {
        dbQuery {
            val backup = getBackup(id) ?: error("Backup not found")
            backup.entries.forEach { entry ->
                if (BackupEntryBackupTable.selectAll().where {
                        BackupEntryBackupTable.entry eq entry.id and
                                (BackupEntryBackupTable.backup neq id)
                    }.empty()
                ) {
                    getBlobFile(entry.hash).toFile().delete()
                    BackupEntryTable.deleteWhere {
                        BackupEntryTable.id eq entry.id
                    }
                }
            }
            BackupTable.deleteWhere { BackupTable.id eq id }
        }
    }

    fun getBlobFile(hash: String): Path {
        return blobDir.resolve(hash.take(2)).resolve(hash.drop(2))
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
                            val blobInput: InputStream
                            if (!blob.exists()) {
                                if (it.value.cloudDriveId != null) {
                                    Class.forName("com.github.zly2006.xbackup.OnedriveUtils")
                                    blobInput = oneDriveService.downloadBlob(it.value.hash)
                                }
                                else {
                                    log.error("Blob not found for file ${it.key}, hash: ${it.value.hash}")
                                    return@async
                                }
                            }
                            else {
                                blobInput = blob.toFile().inputStream().buffered()
                            }
                            path.outputStream().buffered().use { output ->
                                val input = if (it.value.gzip) GZIPInputStream(blobInput) else blobInput
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
                val done = done.incrementAndGet()
                if (verbose || done % 30 == 0 && worked) {
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
            if (!blobFile.exists()) {
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
            val entries =
                BackupEntryBackupTable.select(BackupEntryBackupTable.entry).where {
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
                }.map { it.toBackupEntry() }
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
            this[BackupEntryTable.gzip],
            this[BackupEntryTable.cloudDriveId].let { if (it == 0.toByte()) null else it },
        )
    }
}
