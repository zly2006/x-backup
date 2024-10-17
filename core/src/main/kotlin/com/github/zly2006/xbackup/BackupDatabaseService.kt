package com.github.zly2006.xbackup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createParentDirectories
import kotlin.io.path.fileSize

class BackupDatabaseService(
    private val database: Database,
    private val blobDir: Path
): CoroutineScope {
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

    object BackupEntryTable: IntIdTable("backup_entries") {
        val path = varchar("path", 255).index()
        val size = long("size")
        val zippedSize = long("zipped_size")
        val lastModified = long("last_modified")
        val isDirectory = bool("is_directory")
        val hash = varchar("hash", 255).index()
        val gzip = bool("gzip")
    }

    object BackupTable: IntIdTable("backups") {
        val size = long("size")
        val zippedSize = long("zipped_size")
        val created = long("created")
        val comment = varchar("comment", 255)
    }

    object BackupEntryBackupTable: IntIdTable("backup_entry_backup") {
        val backup = reference("backup", BackupTable, ReferenceOption.CASCADE).index()
        val entry = reference("entry", BackupEntryTable, ReferenceOption.CASCADE).index()
    }

    class BackupEntry(id: EntityID<Int>): IntEntity(id) {
        companion object: IntEntityClass<BackupEntry>(BackupEntryTable)

        var path by BackupEntryTable.path
        var size by BackupEntryTable.size
        var zippedSize by BackupEntryTable.zippedSize
        var lastModified by BackupEntryTable.lastModified
        var isDirectory by BackupEntryTable.isDirectory
        var hash by BackupEntryTable.hash
        var gzip by BackupEntryTable.gzip
        val backups by Backup via BackupEntryBackupTable

        @Serializable
        class Model(
            val path: String,
            val size: Long,
            val lastModified: Long,
            val isDirectory: Boolean,
            val hash: String,
            val gzip: Boolean,
        )

        fun toModel() = Model(
            path,
            size,
            lastModified,
            isDirectory,
            hash,
            gzip
        )
    }

    class Backup(id: EntityID<Int>): IntEntity(id) {
        companion object : IntEntityClass<Backup>(BackupTable)

        var size by BackupTable.size
        var zippedSize by BackupTable.zippedSize
        var created by BackupTable.created
        var comment by BackupTable.comment
        val entries by BackupEntry via BackupEntryBackupTable

        @Serializable
        class Model(
            val size: Long,
            val zippedSize: Long,
            val created: Long,
            val comment: String,
            val entries: List<BackupEntry.Model>,
        )

        fun toModel() = Model(
            size,
            zippedSize,
            created,
            comment,
            entries.map { it.toModel() }.toList()
        )
    }

    suspend fun createBackup(root: Path, predicate: (Path) -> Boolean): BackupResult {
        val backup = dbQuery {
            Backup.new {
                size = 0
                zippedSize = 0
                created = System.currentTimeMillis()
                comment = ""
            }
        }
        val timeStart = System.currentTimeMillis()

        val newEntries = ConcurrentHashMap.newKeySet<BackupEntry>()
        val entries = root.toFile().walk().filter {
            it.name != "x_backup.db" && !it.toPath().normalize().startsWith(blobDir.normalize()) &&
                    predicate(it.toPath())
        }.map {
            @Suppress("SuspendFunctionOnCoroutineScope")
            this.async(Dispatchers.IO) {
                val path = root.normalize().relativize(it.toPath()).normalize()
                val existing = dbQuery {
                    BackupEntry.find {
                        BackupEntryTable.path eq path.toString()
                    }.firstOrNull()
                }
                if (existing != null) {
                    if (it.isDirectory) {
                        if (existing.isDirectory) {
                            return@async existing
                        }
                    }
                    else if (it.isFile) {
                        if (!existing.isDirectory &&
                            it.lastModified() == existing.lastModified && it.length() == existing.size
                        ) {
                            return@async existing
                        }
                    }
                }

                val md5 = if (it.isFile) {
                    it.inputStream().use { stream ->
                        MessageDigest.getInstance("MD5").digest(stream.readBytes())
                    }.joinToString("") { "%02x".format(it) }
                } else ""

                if (md5 == existing?.hash) {
                    return@async existing
                }
                dbQuery {
                    BackupEntry.find {
                        BackupEntryTable.hash eq md5
                    }.firstOrNull()
                }?.let { return@async it }

                val blob = blobDir.resolve(md5.take(2)).resolve(md5.drop(2)).createParentDirectories()
                val gzip = it.length() > 1024
                val zippedSize: Long
                if (it.isFile) {
                    if (!gzip) {
                        try {
                            Files.copy(it.toPath(), blob, StandardCopyOption.REPLACE_EXISTING)
                        } catch (_: FileAlreadyExistsException) {
                            // fuck u macos
                        }
                        zippedSize = it.length()
                    }
                    else {
                        GZIPOutputStream(blob.toFile().outputStream().buffered()).use { stream ->
                            it.inputStream().buffered().use { input ->
                                input.copyTo(stream)
                            }
                        }
                        zippedSize = blob.fileSize()
                    }
                } else {
                    zippedSize = 0
                }

                val backupEntry = dbQuery {
                    BackupEntry.new {
                        this.path = path.toString()
                        size = it.length()
                        lastModified = it.lastModified()
                        isDirectory = it.isDirectory
                        hash = md5
                        this.zippedSize = zippedSize
                        this.gzip = gzip
                    }
                }
                newEntries.add(backupEntry)
                backupEntry
            }
        }.toList().awaitAll()
        dbQuery {
            backup.size = entries.sumOf { it.size }
            backup.zippedSize = entries.sumOf { it.zippedSize }
            entries.forEach { entry ->
                BackupEntryBackupTable.insert {
                    it[this.backup] = backup.id
                    it[this.entry] = entry.id
                }
            }
        }
        return BackupResult(
            true,
            "OK",
            backup.id.value,
            backup.size,
            backup.zippedSize,
            newEntries.sumOf { it.zippedSize },
            System.currentTimeMillis() - timeStart,
        )
    }

    suspend fun deleteBackup(id: Int) {
        dbQuery {
            val backup = Backup.findById(id) ?: error("Backup not found")
            backup.entries.forEach { entry ->
                if (entry.backups.count() == 1L) {
                    blobDir.resolve(entry.hash.take(2)).resolve(entry.hash.drop(2)).toFile().delete()
                    entry.delete()
                }
            }
            backup.delete()
        }
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
        dbQuery {
            val backup = Backup.findById(id) ?: error("Backup not found")
            val map = backup.entries.associateBy { it.path }
            target.toFile().walk().forEach {
                val path = target.normalize().relativize(it.toPath()).normalize()
                if (ignored(path)) return@forEach
                val entry = map[path.toString()]
                if (entry == null) {
                    it.deleteRecursively()
                }
                else if (it.isDirectory != entry.isDirectory) {
                    it.deleteRecursively()
                }
            }
            map.forEach {
                val path = target.resolve(it.key).createParentDirectories()
                if (it.value.isDirectory) {
                    path.toFile().mkdirs()
                }
                else {
                    val blob = blobDir.resolve(it.value.hash.take(2)).resolve(it.value.hash.drop(2))
                    if (it.value.gzip) {
                        blob.toFile().inputStream().buffered().use { input ->
                            GZIPOutputStream(path.toFile().outputStream().buffered()).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    else {
                        Files.copy(blob, path, StandardCopyOption.REPLACE_EXISTING)
                    }
                    path.toFile().setLastModified(it.value.lastModified)
                }
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database, statement = block)
}
