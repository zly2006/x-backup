// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
// <ImportSnippet>
package com.github.zly2006.xbackup.multi

import com.azure.core.credential.TokenRequestContext
import com.azure.identity.*
import com.microsoft.graph.authentication.TokenCredentialAuthProvider
import com.microsoft.graph.http.GraphServiceException
import com.microsoft.graph.logger.LoggerLevel
import com.microsoft.graph.models.*
import com.microsoft.graph.requests.GraphServiceClient
import okhttp3.Request
import reactor.core.publisher.Mono
import java.io.*
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories

object Graph {
    private var _properties: Properties? = null
    private var _deviceCodeCredential: DeviceCodeCredential? = null
    private var _userClient: GraphServiceClient<Request>? = null
    private val scopes = listOf(
        "user.read",
        "Files.ReadWrite.All"
    )

    @JvmStatic
    fun initializeGraphForUserAuth(properties: Properties?, challenge: Consumer<DeviceCodeInfo?>?) {
        // Ensure properties isn't null
        if (properties == null) {
            throw Exception("Properties cannot be null")
        }

        _properties = properties

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

        val authProvider =
            TokenCredentialAuthProvider(graphUserScopes, _deviceCodeCredential!!)

        _userClient = GraphServiceClient.builder()
            .authenticationProvider { CompletableFuture.completedFuture(_deviceCodeCredential!!.getTokenSync(trc).token) }
            .buildClient()

        _userClient!!.logger!!.loggingLevel = LoggerLevel.DEBUG
    }

    @JvmStatic
    @get:Throws(Exception::class)
    val userToken: String
        get() {
            if (_deviceCodeCredential == null) {
                error("Graph has not been initialized for user auth")
            }

            val context = TokenRequestContext()
            context.addScopes(*scopes.toTypedArray())

            val token = _deviceCodeCredential!!.getToken(context).block()
            return token.token
        }

    @JvmStatic
    @get:Throws(Exception::class)
    val user: User?
        // </GetUserTokenSnippet>
        get() {
            // Ensure client isn't null
            if (_userClient == null) {
                error("Graph has not been initialized for user auth")
            }

            return _userClient!!.me()
                .buildRequest()
                .select("displayName,mail,userPrincipalName")
                .get()
        }

    private fun initXBackupDirectory() {
        val item = try {
            requireNotNull(_userClient) { "Graph has not been initialized for user auth" }
                .me()
                .drive()
                .root()
                .itemWithPath("X-Backup")
                .buildRequest()
                .get()
        } catch (e: GraphServiceException) {
            if (e.error?.error?.code == "itemNotFound") {
                requireNotNull(_userClient) { "Graph has not been initialized for user auth" }
                    .me()
                    .drive()
                    .root()
                    .children()
                    .buildRequest()
                    .post(DriveItem().apply {
                        name = "X-Backup"
                        folder = Folder()
                    })
            }
            else throw e
        }
        requireNotNull(item) { "Failed to create X-Backup directory" }
        _userClient!!.me()
            .drive()
            .root()
            .itemWithPath("X-Backup/test.txt")
            .content()
            .buildRequest()
            .put("Hello, World!".encodeToByteArray())
        item.children?.currentPage
        item.remoteItem
    }

    @JvmStatic
    fun listOneDriveFiles() {
        requireNotNull(_userClient) { "Graph has not been initialized for user auth" }

        initXBackupDirectory()
    }
}
