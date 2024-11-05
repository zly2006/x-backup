package com.github.zly2006.xbackup

import com.azure.core.credential.TokenRequestContext
import com.azure.identity.AuthenticationRecord
import com.azure.identity.DeviceCodeCredential
import com.azure.identity.DeviceCodeCredentialBuilder
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.multi.App
import com.microsoft.graph.requests.GraphServiceClient
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import net.minecraft.server.command.ServerCommandSource
import okhttp3.Request
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConnection
import reactor.core.publisher.Mono
import java.io.*
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CompletableFuture
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
    class BackupEntry(
        val id: Int,
        val path: String,
        val size: Long,
        val zippedSize: Long,
        val lastModified: Long,
        val isDirectory: Boolean,
        val hash: String,
        val gzip: Boolean,
        val cloudDriveId: Byte?,
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
                retry(5) {
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
                                    return@retry existing
                                }
                            }
                            else if (sourceFile.isFile) {
                                if (!existing.isDirectory &&
                                    sourceFile.lastModified() == existing.lastModified && sourceFile.length() == existing.size
                                ) {
                                    return@retry existing
                                }
                            }
                        }

                        val md5 = if (sourceFile.isFile) {
                            MessageDigest.getInstance("MD5").digest(sourceFile.readBytes())
                                .joinToString("") { "%02x".format(it) }
                        }
                        else ""

                        if (md5 == existing?.hash) {
                            return@retry existing
                        }
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
                        if (sourceFile.isFile) {
                            if (MessageDigest.getInstance("MD5").digest(sourceFile.readBytes())
                                    .joinToString("") { "%02x".format(it) } != backupEntry.hash
                            ) {
                                XBackup.log.error("File hash mismatch when creating backup, file: $path, expected: ${backupEntry.hash}")
                                error("File hash mismatch")
                            }
                        }
                        newEntries.add(backupEntry)
                        backupEntry
                    } catch (e: IOException) {
                        throw IOException("Error backing up file: $sourceFile", e)
                    }
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

    private suspend fun <T> retry(times: Int, function: suspend () -> T): T {
        var lastException: Throwable? = null
        repeat(times) {
            try {
                return function()
            } catch (e: Throwable) {
                XBackup.log.error("Error in retry, attempt ${it + 1}/$times", e)
                lastException = e
                delay(1000)
            }
        }
        throw lastException!!
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

    lateinit var client: GraphServiceClient<Request>

    suspend fun uploadOneDrive(id: Int) {
        val backup = getBackup(id) ?: error("Backup not found")
        backup.entries.filter { it.cloudDriveId == null }.map { entry ->
            client.me().drive().root().itemWithPath("X-Backup/blob/${entry.hash}")
                .content()
                .buildRequest()
                .putAsync(getBlobFile(entry.hash).readBytes())
                .thenApply {
                    GlobalScope.launch {
                        dbQuery {
                            BackupEntryTable.update({ BackupEntryTable.id eq entry.id }) {
                                it[cloudDriveId] = 1
                            }
                            getBlobFile(entry.hash).toFile().delete()
                        }
                    }
                }
        }.map { it.await() }.joinAll()
        val uuid = UUID.randomUUID().toString()
        val localBackup = File("x_backup.db.back")
        try {
            (database.connector().connection as? SQLiteConnection)?.createStatement()
                ?.execute("VACUUM INTO 'x_backup.db.back';")
        } catch (e: Exception) {
            XBackup.log.error("Error backing up database", e)
        }
        client.me().drive().root().itemWithPath("X-Backup/db/$uuid.x_backup.db.back")
            .content()
            .buildRequest()
            .putAsync(localBackup.readBytes())
            .thenApply {
                localBackup.delete()
            }.await()
        XBackup.log.info("Uploaded backup $id to OneDrive")
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
    suspend fun restore(id: Int, target: Path, ignored: (Path) -> Boolean) {
        coroutineScope {
            dbQuery {
                val backup = getBackup(id) ?: error("Backup not found")
                val map = backup.entries.associateBy { it.path }.filter { !ignored(Path(it.key)) }
                for (it in target.toFile().walk()) {
                    val path = target.normalize().relativize(it.toPath()).normalize()
                    if (it.name in ignoredFiles || ignored(path))
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
                        retry(5) {
                            val path = target.resolve(it.key).normalize().createParentDirectories()
                            if (it.value.isDirectory) {
                                path.toFile().mkdirs()
                            }
                            else {
                                if (!path.exists()) {
                                    path.createParentDirectories().createFile()
                                }
                                val blob = getBlobFile(it.value.hash)
                                val blobInput: InputStream
                                if (!blob.exists()) {
                                    if (it.value.cloudDriveId != null) {
                                        blobInput =
                                            client.me().drive().root().itemWithPath("X-Backup/blob/${it.value.hash}")
                                                .content()
                                                .buildRequest()
                                                .get()!!
                                    }
                                    else {
                                        XBackup.log.error("Blob not found for file ${it.key}, hash: ${it.value.hash}")
                                        return@retry
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
                                    XBackup.log.error(
                                        "File hash mismatch, file: $path, expected: ${it.value.hash}, actual: $checkAgain, gzip: $gzipMd5" +
                                                if (it.value.hash == gzipMd5 && gzipMd5 != checkAgain) " (writing file failed?)"
                                                else if (it.value.hash != gzipMd5 && gzipMd5 == checkAgain) " (bad md5 when creating backup?)"
                                                else " (WTF???)"
                                    )
                                    path.writeBytes(bytes)
                                }
                                require(path.fileSize() == it.value.size)
                                path.toFile().setLastModified(it.value.lastModified)
                                XBackup.log.info("Restored file ${it.key}")
                            }
                        }
                    }
                }.awaitAll()
                XBackup.log.info("Restored backup $id")
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

    val scopes = listOf(
        "user.read",
        "Files.ReadWrite.All"
    )
    private var _deviceCodeCredential: DeviceCodeCredential? = null

    fun initializeGraphForUserAuth(source: ServerCommandSource) {
        val properties = Properties().apply {
            load(XBackup::class.java.getResourceAsStream("/oAuth.properties"))
        }

        val clientId = properties.getProperty("app.clientId")
        val tenantId = properties.getProperty("app.tenantId")
        val graphUserScopes = scopes

        val authenticationRecordPath = "path/to/authentication-record.json"
        Path(authenticationRecordPath).createParentDirectories()
        var authenticationRecord: AuthenticationRecord? = null
        try {
            // If we have an existing record, deserialize it.
            if (Files.exists(File(authenticationRecordPath).toPath())) {
                authenticationRecord = AuthenticationRecord.deserialize(FileInputStream(authenticationRecordPath))
            }
        } catch (e: FileNotFoundException) {
            // Handle error as appropriate.
        }

        val builder = DeviceCodeCredentialBuilder().clientId(clientId).tenantId(tenantId)
        if (authenticationRecord != null) {
            // As we have a record, configure the builder to use it.
            builder.authenticationRecord(authenticationRecord)
        }
        builder.challengeConsumer {
            source.send(literalText(it.message))
        }
        _deviceCodeCredential = builder.build()
        val trc = TokenRequestContext().addScopes(*scopes.toTypedArray())
        if (authenticationRecord == null) {
            // We don't have a record, so we get one and store it. The next authentication will use it.
            _deviceCodeCredential!!.authenticate(trc).flatMap { record: AuthenticationRecord ->
                try {
                    return@flatMap record.serializeAsync(FileOutputStream(authenticationRecordPath))
                } catch (e: FileNotFoundException) {
                    return@flatMap Mono.error(e)
                }
            }.subscribe()
        }

        client = GraphServiceClient.builder()
            .authenticationProvider { CompletableFuture.completedFuture(_deviceCodeCredential!!.getTokenSync(trc).token) }
            .buildClient()
    }
}

private fun ResultRow.toBackup() = BackupDatabaseService.Backup(
    this[BackupDatabaseService.BackupTable.id].value,
    this[BackupDatabaseService.BackupTable.size],
    this[BackupDatabaseService.BackupTable.zippedSize],
    this[BackupDatabaseService.BackupTable.created],
    this[BackupDatabaseService.BackupTable.comment],
    BackupDatabaseService.BackupEntryTable.selectAll().where {
        BackupDatabaseService.BackupEntryTable.id inSubQuery
                BackupDatabaseService.BackupEntryBackupTable.select(BackupDatabaseService.BackupEntryBackupTable.entry)
                    .where {
                        BackupDatabaseService.BackupEntryBackupTable.backup eq this@toBackup[BackupDatabaseService.BackupTable.id]
                    }
    }.map { it.toBackupEntry() }
)

private fun ResultRow.toBackupEntry() = BackupDatabaseService.BackupEntry(
    this[BackupDatabaseService.BackupEntryTable.id].value,
    this[BackupDatabaseService.BackupEntryTable.path],
    this[BackupDatabaseService.BackupEntryTable.size],
    this[BackupDatabaseService.BackupEntryTable.zippedSize],
    this[BackupDatabaseService.BackupEntryTable.lastModified],
    this[BackupDatabaseService.BackupEntryTable.isDirectory],
    this[BackupDatabaseService.BackupEntryTable.hash],
    this[BackupDatabaseService.BackupEntryTable.gzip],
    this[BackupDatabaseService.BackupEntryTable.cloudDriveId].let { if (it == 0.toByte()) null else it },
)
