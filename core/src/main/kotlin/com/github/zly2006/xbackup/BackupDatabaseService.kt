package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.BackupDatabaseService.BackupEntryTable
import com.github.zly2006.xbackup.BackupDatabaseService.BackupTable
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.*

class BackupDatabaseService(
    private val database: Database,
    private val blobDir: Path,
) : CoroutineScope {
    private val ignoredFiles = setOf(
        "x_backup.db",
        "x_backup.db-journal",
        "session.lock",
    )

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
    class BackupEntry(
        val id: Int,
        val path: String,
        val size: Long,
        val zippedSize: Long,
        val lastModified: Long,
        val isDirectory: Boolean,
        val hash: String,
        val gzip: Boolean,
    )

    @Serializable
    class Backup(
        val id: Int,
        val size: Long,
        val zippedSize: Long,
        val created: Long,
        val comment: String,
        val entries: List<BackupEntry>,
    )

    suspend fun createBackup(root: Path, comment: String, predicate: (Path) -> Boolean): BackupResult {
        val timeStart = System.currentTimeMillis()

        val newEntries = ConcurrentHashMap.newKeySet<BackupEntry>()
        val entries = root.toFile().walk().filter {
            it.name !in ignoredFiles && !it.toPath().normalize().startsWith(blobDir.normalize()) &&
                    predicate(it.toPath())
        }.map { sourceFile ->
            @Suppress("SuspendFunctionOnCoroutineScope")
            this.async(Dispatchers.IO) {
                try {
                    val path = root.normalize().relativize(sourceFile.toPath()).normalize()
                    val existing = dbQuery {
                        BackupEntryTable.selectAll().where {
                            BackupEntryTable.path eq path.toString()
                        }.firstOrNull()?.toBackupEntry()
                    }
                    if (existing != null) {
                        if (sourceFile.isDirectory) {
                            if (existing.isDirectory) {
                                return@async existing
                            }
                        }
                        else if (sourceFile.isFile) {
                            if (!existing.isDirectory &&
                                sourceFile.lastModified() == existing.lastModified && sourceFile.length() == existing.size
                            ) {
                                return@async existing
                            }
                        }
                    }

                    val md5 = if (sourceFile.isFile) {
                        MessageDigest.getInstance("MD5").digest(sourceFile.readBytes())
                            .joinToString("") { "%02x".format(it) }
                    }
                    else ""

                    if (md5 == existing?.hash) {
                        return@async existing
                    }
                    dbQuery {
                        BackupEntryTable.selectAll().where {
                            BackupEntryTable.hash eq md5
                        }.firstOrNull()?.toBackupEntry()
                    }?.let { return@async it }

                    val blob = blobDir.resolve(md5.take(2)).resolve(md5.drop(2))
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

                    val backupEntry = dbQuery {
                        BackupEntryTable.insert {
                            it[this.path] = path.toString()
                            it[this.size] = sourceFile.length()
                            it[this.lastModified] = sourceFile.lastModified()
                            it[this.isDirectory] = sourceFile.isDirectory
                            it[this.hash] = md5
                            it[this.zippedSize] = zippedSize
                            it[this.gzip] = gzip
                        }
                    }.resultedValues!!.single().toBackupEntry()
//
//                if (sourceFile.isFile) {
//                    delay(5000)
//                    require(MessageDigest.getInstance("MD5").digest(sourceFile.readBytes())
//                        .joinToString("") { "%02x".format(it) } == backupEntry.hash)
//                }
                    newEntries.add(backupEntry)
                    backupEntry
                }
                catch (e: IOException) {
                    throw IOException("Error backing up file: $sourceFile", e)
                }
            }
        }.toList().awaitAll()
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
            backup
        }
        dbQuery {
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

    suspend fun deleteBackup(id: Int) {
        dbQuery {
            val backup = getBackup(id) ?: error("Backup not found")
            backup.entries.forEach { entry ->
                if (BackupEntryBackupTable.selectAll().where {
                        BackupEntryBackupTable.entry eq entry.id and
                                (BackupEntryBackupTable.backup neq id)
                    }.empty()
                ) {
                    blobDir.resolve(entry.hash.take(2)).resolve(entry.hash.drop(2)).toFile().delete()
                    BackupEntryTable.deleteWhere {
                        BackupEntryTable.id eq entry.id
                    }
                }
            }
            BackupTable.deleteWhere { BackupTable.id eq id }
        }
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
    suspend fun restore(id: Int, target: Path, ignored: (Path) -> Boolean) {
        coroutineScope {
            dbQuery {
                val backup = getBackup(id) ?: error("Backup not found")
                val map = backup.entries.associateBy { it.path }
                for (it in target.toFile().walk()) {
                    val path = target.normalize().relativize(it.toPath()).normalize()
                    if (it.name in ignoredFiles)
                        continue
                    if (ignored(path))
                        continue
                    val entry = map[path.toString()]
                    if (entry == null) {
                        it.deleteRecursively()
                    }
                    else if (it.isDirectory != entry.isDirectory) {
                        it.deleteRecursively()
                    }
                }
                map.map {
                    this@BackupDatabaseService.async {
                        val path = target.resolve(it.key).normalize().createParentDirectories()
                        if (it.value.isDirectory) {
                            path.toFile().mkdirs()
                        }
                        else {
                            if (!path.exists()) {
                                path.createParentDirectories().createFile()
                            }
                            val blob = blobDir.resolve(it.value.hash.take(2)).resolve(it.value.hash.drop(2))
                            if (it.value.gzip) {
//                            GZIPInputStream(blob.toFile().inputStream().buffered()).use { stream ->
//                                path.outputStream().buffered().use { output ->
//                                    stream.copyTo(output)
//                                }
//                            }
                                val bytes = GZIPInputStream(blob.toFile().inputStream().buffered()).readBytes()
                                path.toFile().writeBytes(bytes)
                            }
                            else {
                                blob.toFile().inputStream().buffered().use { input ->
                                    path.outputStream().buffered().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            val checkAgain =
                                MessageDigest.getInstance("MD5").digest(path.toFile().inputStream().readBytes())
                                    .joinToString("") { "%02x".format(it) }
                            if (checkAgain != it.value.hash) {
                                val bytes = GZIPInputStream(blob.toFile().inputStream().buffered()).readBytes()
                                val gzipMd5 = MessageDigest.getInstance("MD5").digest(bytes)
                                    .joinToString("") { "%02x".format(it) }
                                System.err.println("File hash mismatch, file: $path, expected: ${it.value.hash}, actual: $checkAgain, gzip: $gzipMd5")
                                path.writeBytes(bytes)
                            }
                            require(path.fileSize() == it.value.size)
                            path.toFile().setLastModified(it.value.lastModified)
                        }
                    }
                }.awaitAll()
                println("Restored backup $id")
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database, statement = block)

    fun listBackups(offset: Int, limit: Int): List<Backup> {
        return transaction {
            BackupTable.selectAll()
                .orderBy(BackupTable.id to SortOrder.DESC)
                .limit(limit, offset.toLong()).toList()
                .map { it.toBackup() }
        }
    }
}

private fun ResultRow.toBackup() = BackupDatabaseService.Backup(
    this[BackupTable.id].value,
    this[BackupTable.size],
    this[BackupTable.zippedSize],
    this[BackupTable.created],
    this[BackupTable.comment],
    BackupEntryTable.selectAll().where {
        BackupEntryTable.id inSubQuery
                BackupDatabaseService.BackupEntryBackupTable.select(BackupDatabaseService.BackupEntryBackupTable.entry)
                    .where {
                        BackupDatabaseService.BackupEntryBackupTable.backup eq this@toBackup[BackupTable.id]
                    }
    }.map { it.toBackupEntry() }
)

private fun ResultRow.toBackupEntry() = BackupDatabaseService.BackupEntry(
    this[BackupEntryTable.id].value,
    this[BackupEntryTable.path],
    this[BackupEntryTable.size],
    this[BackupEntryTable.zippedSize],
    this[BackupEntryTable.lastModified],
    this[BackupEntryTable.isDirectory],
    this[BackupEntryTable.hash],
    this[BackupEntryTable.gzip],
)
