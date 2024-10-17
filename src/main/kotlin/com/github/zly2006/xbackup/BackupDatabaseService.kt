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
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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
        val hash = varchar("hash", 255)
        val gzip = bool("gzip")
    }

    object BackupTable: IntIdTable("backups") {
        val size = long("size")
        val zippedSize = long("zipped_size")
        val created = long("created")
        val hash = varchar("hash", 255)
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
        val backups by Backup referrersOn BackupEntryBackupTable.backup

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
        val entries by BackupEntry referrersOn BackupEntryBackupTable.entry

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

    suspend fun createBackup(path: Path, predicate: (Path) -> Boolean) {
        val backup = dbQuery {
            Backup.new {
                size = 0
                zippedSize = 0
                created = System.currentTimeMillis()
                comment = ""
            }
        }

        val entries = path.toFile().walk().filter { predicate(it.toPath()) }.map {
            @Suppress("SuspendFunctionOnCoroutineScope")
            this.async(Dispatchers.IO) {
                val existing = dbQuery {
                    BackupEntry.find {
                        BackupEntryTable.path eq it.toPath().toString()
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

                val blob = blobDir.resolve(md5.take(2)).resolve(md5.drop(2))
                blob.createParentDirectories()
                val gzip = it.length() > 1024
                val zippedSize: Long
                if (!gzip) {
                    Files.copy(it.toPath(), blob)
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

                return@async BackupEntry.new {
                    this.path = it.toPath().toString()
                    size = it.length()
                    lastModified = it.lastModified()
                    isDirectory = it.isDirectory
                    hash = md5
                    this.zippedSize = zippedSize
                    this.gzip = gzip
                }
            }
        }.toList().awaitAll()
        backup.size = entries.sumOf { it.size }
        backup.zippedSize = entries.sumOf { it.zippedSize }
        dbQuery {
            entries.forEach { entry ->
                BackupEntryBackupTable.insert {
                    it[this.backup] = backup.id
                    it[this.entry] = entry.id
                }
            }
        }
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

    suspend fun restore(id: Int, target: Path, ignored: (Path) -> Boolean) {
        dbQuery {
            val backup = Backup.findById(id) ?: error("Backup not found")
            val map = backup.entries.associateBy { it.path }
            target.toFile().walk().forEach {
                if (ignored(it.toPath())) return@forEach
                val entry = map[it.toPath().toString()]
                if (entry == null) {
                    it.delete()
                }
                else {
                    if (it.isDirectory) {
                        if (!entry.isDirectory) {
                            it.delete()
                        }
                    }
                    else if (it.isFile) {
                        if (entry.isDirectory || it.lastModified() != entry.lastModified || it.length() != entry.size) {
                            it.delete()
                        }
                    }
                }
            }
            map.forEach {
                val path = target.resolve(it.key)
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
                        Files.copy(blob, path)
                    }
                    path.toFile().setLastModified(it.value.lastModified)
                }
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database, statement = block)
}
