package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.Utils.broadcast
import com.github.zly2006.xbackup.api.XBackupApi
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Util
import net.minecraft.util.WorldSavePath
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteConnection
import org.sqlite.SQLiteDataSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.*

object XBackup : ModInitializer {
    lateinit var config: Config
    private val configPath = FabricLoader.getInstance().configDir.resolve("x-backup.config.json")
    val log = LoggerFactory.getLogger("XBackup")!!
    const val MOD_VERSION = /*$ mod_version*/ "0.3.4-pre.7"
    const val GIT_COMMIT = /*$ git_commit*/ "278ce31"
    const val COMMIT_DATE = /*$ commit_date*/ "2024-12-06T22:28:50+08:00"
    lateinit var service: BackupDatabaseService
    lateinit var server: MinecraftServer

    @get:JvmName("isServerStarted")
    val serverStarted get() = ::server.isInitialized
    var restoring = false
    var serverStopHook: (MinecraftServer) -> Unit = {}

    // Backup
    var isBusy = false

    // Restore
    var reason = ""
        set(value) {
            field = value
            log.info("Restore reason: $value")
        }
    var blockPlayerJoin = false
    var disableSaving = false
    var disableWatchdog = false

    enum class BackgroundState {
        IDLE, UNKNOWN, SCHEDULED_BACKUP, PRUNING, STOPPED
    }

    var backgroundState = BackgroundState.UNKNOWN
    var crontabJob: Job? = null

    fun loadConfig() {
        try {
            config = (if (configPath.exists()) Json.decodeFromString(configPath.readText())
            else Config())
            config.language = I18n.setLanguage(config.language)
        } catch (e: Exception) {
            log.error("Error loading config", e)
            config = Config()
        }
        saveConfig()
    }

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        encodeDefaults = true
        prettyPrint = true
        allowTrailingComma = true
    }

    fun saveConfig() {
        try {
            configPath.writeText(json.encodeToString(config))
        } catch (e: Exception) {
            log.error("Error saving config", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun onInitialize() {
        runCatching {
            loadConfig()
        }
        if (config.mirrorMode) {
            if (config.mirrorFrom == null) {
                log.error("Mirror mode is enabled but mirrorFrom is not set")
                error("Mirror mode is enabled but mirrorFrom is not set")
            }
            val mirrorFrom = File(config.mirrorFrom!!)
            if (!mirrorFrom.isDirectory) {
                log.error("Mirror mode is enabled but mirrorFrom is not a directory")
                error("Mirror mode is enabled but mirrorFrom is not a directory")
            }
            if (!mirrorFrom.resolve("server.properties").exists() || !mirrorFrom.resolve("world").exists()) {
                log.error("Mirror mode is enabled but mirrorFrom is not a valid server directory")
                error("Mirror mode is enabled but mirrorFrom is not a valid server directory")
            }
        }
        if (System.getProperty("xb.restart") == "true") {
            when (Util.getOperatingSystem()) {
                Util.OperatingSystem.OSX, Util.OperatingSystem.LINUX -> {
                    ProcessBuilder(RestartUtils.generateUnixRestartCommand())
                        .start()
                }

                else -> {
                    error("Unsupported operating system")
                }
            }

            log.info("Restarting...")
            Runtime.getRuntime().exit(0)
        }
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ ->
            Commands.register(dispatcher)
        })
        ServerLifecycleEvents.SERVER_STARTING.register {
            restoring = false
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            this.server = server
            kotlin.runCatching {
                // sync client language to the integrated server
                config.language = I18n.setLanguage(MinecraftClient.getInstance().options.language)
            }
            val worldPath = if (config.mirrorMode) {
                File(config.mirrorFrom!!).toPath().resolve("world")
            }
            else {
                server.getSavePath(WorldSavePath.ROOT)
            }.toAbsolutePath()
            val database = getDatabaseFromWorld(worldPath)
            if (config.mirrorMode) {
                val sourceConfig = kotlin.runCatching {
                    Json.decodeFromStream<Config>(
                        Path(
                            config.mirrorFrom!!,
                            "config",
                            "x-backup.config.json"
                        ).inputStream()
                    )
                }.getOrNull()
                if (sourceConfig == null) {
                    log.error("Failed to load config from source server!")
                }
                val config = sourceConfig ?: config
                service = BackupDatabaseService(
                    database,
                    Path(this.config.mirrorFrom!!).resolve(config.blobPath).absolute().normalize(),
                    config
                )
            }
            else {
                service =
                    BackupDatabaseService(database, Path("").absolute().resolve(config.blobPath).normalize(), config)
            }
            XBackupApi.setInstance(service)
            if (config.cloudBackupToken != null) {
                val httpClient = HttpClient(Apache5) {
                    engine {
                        followRedirects = true
                        socketTimeout = 20_000
                        connectTimeout = 20_000
                        connectionRequestTimeout = 20_000
                        customizeClient {

                        }
                        customizeRequest {
                        }
                    }

                    install(ContentNegotiation) {
                        json(this@XBackup.json)
                    }
                    install(HttpRedirect)
                    install(HttpTimeout) {
                        requestTimeoutMillis = 60_000
                        connectTimeoutMillis = 60_000
                        socketTimeoutMillis = 60_000
                    }
                    install(UserAgent) {
                        agent = "XBackup/$MOD_VERSION RedenMC/0.1-x-backup"
                    }
                    install(HttpRequestRetry) {
                        retryOnServerErrors(1)
                    }
                }
                service.cloudStorageProvider = OnedriveSupport(config, httpClient)
            }
            if (!config.mirrorMode) {
                GlobalScope.launch(server.asCoroutineDispatcher()) {
                    while (XBackup.server.running) {
                        delay(1000)
                        val cs = service.cloudStorageProvider
                        if (service.activeTaskProgress != -1 &&
                            (cs.bytesSentLastSecond > 0 || cs.bytesReceivedLastSecond > 0)
                        ) {
                            runCatching {
                                server.playerManager.sendToAll(
                                    PlayerListHeaderS2CPacket(
                                        Text.empty(),
                                        Text.literal("X Backup Network Stat\n")
                                            .append(Commands.networkStatsText())
                                    )
                                )
                            }
                        }
                    }
                }
                startCrontabJob(server)
            }
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            XBackupApi.setInstance(null)
            runBlocking {
                crontabJob?.cancelAndJoin()
            }
        }
    }

    fun getDatabaseFromWorld(worldPath: Path?): Database {
        val database = Database.connect(
            SQLiteDataSource(
                SQLiteConfig().apply {
                    enforceForeignKeys(true)
                    setCacheSize(100_000)
                    setJournalMode(SQLiteConfig.JournalMode.WAL)
                }
            ).apply {
                url = "jdbc:sqlite:$worldPath/x_backup.db"
            }
        )
        return database
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startCrontabJob(server: MinecraftServer) {
        require(!config.mirrorMode) {
            "Crontab job should not be started in mirror mode"
        }
        crontabJob = GlobalScope.launch {
            while (true) {
                backgroundState = BackgroundState.IDLE
                delay(10000)
                val backup = service.getLatestBackup()
                if (isBusy) continue
                if (config.pruneConfig.enabled) {
                    backgroundState = BackgroundState.PRUNING
                    prune(server)
                }
                if (config.backupInterval > 0) {
                    backgroundState = BackgroundState.SCHEDULED_BACKUP
                    if (backup == null || (System.currentTimeMillis() - backup.created) / 1000 > config.backupInterval) {
                        try {
                            isBusy = true
                            server.broadcast(Utils.translate("message.xb.running_scheduled_backup"))
                            val (_, _, backId, totalSize, compressedSize, addedSize, millis) = service.createBackup(
                                server.getSavePath(WorldSavePath.ROOT).toAbsolutePath(),
                                I18n.langMap["message.xb.scheduled_backup"] ?: "Scheduled backup",
                                metadata = buildJsonObject {
                                    put("scheduled", true)
                                    put("interval", config.backupInterval)
                                    put("mod_ver", MOD_VERSION)
                                }
                            ) { true }
                            val localBackup = File("x_backup.db.back")
                            localBackup.delete()
                            try {
                                (service.database.connector().connection as? SQLiteConnection)?.createStatement()
                                    ?.execute("VACUUM INTO '$localBackup';")
                            } catch (e: Exception) {
                                log.error("Error backing up database", e)
                            }
                            Files.move(
                                localBackup.toPath(),
                                Path("xb.backups")
                                    .resolve(backId.toString())
                                    .resolve("x_backup.db")
                                    .createParentDirectories(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                            server.broadcast(
                                Utils.translate(
                                    "message.xb.scheduled_backup_finished",
                                    backupIdText(backId),
                                    sizeText(totalSize),
                                    sizeText(compressedSize),
                                    sizeText(addedSize),
                                    millis
                                )
                            )
                        } catch (e: Exception) {
                            log.error("Crontab backup failed", e)
                        } finally {
                            isBusy = false
                        }
                    }
                }
            }
        }.apply {
            invokeOnCompletion {
                backgroundState = BackgroundState.STOPPED
            }
        }
    }

    suspend fun prune(server: MinecraftServer): Int {
        val backups = service.listBackups(0, Int.MAX_VALUE).filter {
            it.created < System.currentTimeMillis() - config.pruneConfig.temporaryKeepPolicy()
        }
        val latest = service.getLatestBackup()

        val idToTime = backups.filter {
            !it.temporary && it.metadata?.get("scheduled")?.jsonPrimitive?.booleanOrNull == true
        }.associate { it.id.toString() to it.created }
        val toPrune = config.pruneConfig.prune(idToTime, System.currentTimeMillis())
        var count = 0
        if (toPrune.isNotEmpty()) {
            try {
                isBusy = true
                server.broadcast(Utils.translate("message.xb.running_prune"))
                toPrune.forEach {
                    if (it == latest?.id?.toString()) {
                        // skip the latest backup
                        // Some players configured keep policy wrongly, and prune keeps deleting the latest backup
                        // and then do scheduled backup again and again
                        return@forEach
                    }
                    server.broadcast(Utils.translate("message.xb.pruning_backup", it))
                    service.deleteBackup(service.getBackup(it.toInt())!!)
                    count++
                }
                server.broadcast(Utils.translate("message.xb.prune_finished", toPrune.size))
            } catch (e: Exception) {
                log.error("Crontab prune failed", e)
            } finally {
                isBusy = false
            }
        }

        backups.filter { it.temporary }.forEach {
            service.deleteBackup(it)
            count++
        }
        return count
    }

    fun ensureNotBusy(
        context: CoroutineContext = server.asCoroutineDispatcher(),
        source: ServerCommandSource? = null,
        block: suspend () -> Unit
    ) {
        require(server.isOnThread)
        if (isBusy) {
            throw SimpleCommandExceptionType(Text.of("Backup is already running")).create()
        }
        isBusy = true
        service.launch(context) {
            try {
                block()
            }
            catch (e: Throwable) {
                log.error("Error running X Backup task", e)
                source?.sendError(Text.of("Error running X Backup task: ${e.message}"))
            }
            finally {
                isBusy = false
            }
        }
    }
}
