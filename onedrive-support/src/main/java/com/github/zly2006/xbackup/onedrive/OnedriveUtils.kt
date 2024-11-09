package com.github.zly2006.xbackup.onedrive

import com.azure.core.credential.TokenRequestContext
import com.azure.identity.AuthenticationRecord
import com.azure.identity.DeviceCodeCredential
import com.azure.identity.DeviceCodeCredentialBuilder
import com.github.zly2006.xbackup.BackupDatabaseService
import com.github.zly2006.xbackup.IOnedriveUtils
import com.github.zly2006.xbackup.Utils.send
import com.github.zly2006.xbackup.XBackup
import com.github.zly2006.xbackup.literalText
import com.microsoft.graph.authentication.TokenCredentialAuthProvider
import com.microsoft.graph.requests.GraphServiceClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.io.files.FileNotFoundException
import net.minecraft.server.command.ServerCommandSource
import okhttp3.Request
import org.jetbrains.exposed.sql.update
import org.sqlite.SQLiteConnection
import reactor.core.publisher.Mono
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes

class OnedriveUtils : IOnedriveUtils {
    private val scopes = listOf(
        "user.read",
        "Files.ReadWrite.All"
    )
    private lateinit var client: GraphServiceClient<Request>
    private var _deviceCodeCredential: DeviceCodeCredential? = null

    init {
        initializeGraphForUserAuth(null, false)
    }

    override fun initializeGraphForUserAuth(source: ServerCommandSource?, login: Boolean) {
        val properties = Properties().apply {
            load(XBackup::class.java.getResourceAsStream("/oAuth.properties"))
        }

        val clientId = properties.getProperty("app.clientId")
        val tenantId = properties.getProperty("app.tenantId")

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
            source?.send(literalText(it.message))
        }
        _deviceCodeCredential = builder.build()
        val trc = TokenRequestContext().addScopes(*scopes.toTypedArray())
        if (login && authenticationRecord == null) {
            // We don't have a record, so we get one and store it. The next authentication will use it.
            _deviceCodeCredential!!.authenticate(trc).flatMap { record: AuthenticationRecord ->
                try {
                    source?.send(literalText("Logged in"))
                    return@flatMap record.serializeAsync(FileOutputStream(authenticationRecordPath))
                } catch (e: FileNotFoundException) {
                    return@flatMap Mono.error(e)
                }
            }.subscribe()
        }

        client = GraphServiceClient.builder()
            .authenticationProvider(TokenCredentialAuthProvider(scopes, _deviceCodeCredential!!))
            .buildClient()
    }


    override suspend fun uploadOneDrive(service: BackupDatabaseService, id: Int) {
        val backup = service.getBackup(id) ?: error("Backup not found")
        val entries = backup.entries.filter { it.cloudDriveId == null && !it.isDirectory }
        val total = entries.size
        var done = 0
        entries.map { entry ->
            if (!service.getBlobFile(entry.hash).exists()) {
                XBackup.log.warn("Blob not found for file ${entry.path}, hash: ${entry.hash}")
                return@map CompletableFuture.completedFuture(null)
            }
            client.me().drive().root().itemWithPath("X-Backup/blob/${entry.hash}")
                .content()
                .buildRequest()
                .putAsync(service.getBlobFile(entry.hash).readBytes())
                .thenApply {
                    GlobalScope.launch {
                        service.dbQuery {
                            BackupDatabaseService.BackupEntryTable.update({ BackupDatabaseService.BackupEntryTable.id eq entry.id }) {
                                it[cloudDriveId] = 1
                            }
                            service.getBlobFile(entry.hash).toFile().delete()
                            done++
                            XBackup.log.info("Uploaded blob ${entry.hash}, $done/$total")
                        }
                    }
                }
        }.mapNotNull { it.await() }.joinAll()
        val uuid = UUID.randomUUID().toString()
        val localBackup = File("x_backup.db.back")
        try {
            (service.database.connector().connection as? SQLiteConnection)?.createStatement()
                ?.execute("VACUUM INTO '$localBackup';")
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

    override fun downloadBlob(hash: String): InputStream {
        return client.me().drive().root().itemWithPath("X-Backup/blob/${hash}")
            .content()
            .buildRequest()
            .get()!!
    }
}
