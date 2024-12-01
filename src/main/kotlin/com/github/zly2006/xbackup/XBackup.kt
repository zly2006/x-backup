package com.github.zly2006.xbackup

import RestartUtils
import com.github.zly2006.xbackup.Utils.broadcast
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.server.MinecraftServer
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
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.*

object XBackup : ModInitializer {
    lateinit var config: Config
    private val configPath = FabricLoader.getInstance().configDir.resolve("x-backup.config.json")
    val log = LoggerFactory.getLogger("XBackup")!!
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

    private var crontabJob: Job? = null

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
    private val json = Json {
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
            } else {
                server.getSavePath(WorldSavePath.ROOT)
            }.toAbsolutePath()
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
            service = BackupDatabaseService(database, Path(".").absolute().resolve(config.blobPath).normalize(), config)
            crontabJob = GlobalScope.launch {
                while (true) {
                    delay(10000)
                    if (config.backupInterval == 0) continue
                    val backup = service.getLatestBackup()
                    if (isBusy) continue
                    if (config.pruneConfig.enabled) {
                        val idToTime = service.listBackups(0, Int.MAX_VALUE)
                            .filter { !it.temporary }
                            .associate { it.id.toString() to it.created }
                        val toPrune = config.pruneConfig.prune(idToTime, System.currentTimeMillis())
                        if (toPrune.isNotEmpty()) {
                            try {
                                isBusy = true
                                server.broadcast(Utils.translate("message.xb.running_prune"))
                                toPrune.forEach {
                                    server.broadcast(Utils.translate("message.xb.pruning_backup", it))
                                    service.deleteBackup(service.getBackup(it.toInt())!!)
                                }
                                server.broadcast(Utils.translate("message.xb.prune_finished", toPrune.size))
                            } catch (e: Exception) {
                                log.error("Crontab prune failed", e)
                            } finally {
                                isBusy = false
                            }
                        }
                        service.listBackups(0, Int.MAX_VALUE).filter {
                            it.temporary && it.created < System.currentTimeMillis() - config.pruneConfig.temporaryKeepPolicy()
                        }.forEach {
                            service.deleteBackup(it)
                        }
                    }
                    if (backup == null || (System.currentTimeMillis() - backup.created) / 1000 > config.backupInterval) {
                        try {
                            isBusy = true
                            server.broadcast(Utils.translate("message.xb.running_scheduled_backup"))
                            val (_, _, backId, totalSize, compressedSize, addedSize, millis) = service.createBackup(
                                server.getSavePath(WorldSavePath.ROOT).toAbsolutePath(),
                                I18n.langMap["message.xb.scheduled_backup"] ?: "Scheduled backup",
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
                                    .createParentDirectories()
                            )
                            server.broadcast(
                                Utils.translate(
                                    "message.xb.scheduled_backup_finished",
                                    backupIdText(backId), sizeText(totalSize), sizeText(compressedSize), sizeText(addedSize), millis
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
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            runBlocking {
                crontabJob?.cancelAndJoin()
            }
        }
    }

    fun ensureNotBusy(context: CoroutineContext = server.asCoroutineDispatcher(), block: suspend () -> Unit) {
        require(server.isOnThread)
        if (isBusy) {
            throw SimpleCommandExceptionType(Text.of("Backup is already running")).create()
        }
        isBusy = true
        service.launch(context) {
            try {
                block()
            } finally {
                isBusy = false
            }
        }
    }
}
