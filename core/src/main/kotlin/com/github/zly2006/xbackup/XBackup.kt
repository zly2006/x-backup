package com.github.zly2006.xbackup

import RestartUtils
import com.github.zly2006.xbackup.Commands.sizeToString
import com.github.zly2006.xbackup.Utils.broadcast
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
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
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

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
        config = Json.decodeFromString(configPath.readText())
    }

    fun saveConfig() {
        configPath.writeText(Json.encodeToString(config))
    }

    override fun onInitialize() {
        runCatching {
            if (configPath.exists())
                loadConfig()
            else {
                config = Config()
                saveConfig()
            }
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
        ServerLifecycleEvents.SERVER_STARTING.register {
            restoring = false
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            this.server = server
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
            service = BackupDatabaseService(database, worldPath.resolve(config.blobPath))
            crontabJob = GlobalScope.launch {
                while (true) {
                    delay(10000)
                    val backup = service.getLatestBackup()
                    if (isBusy) continue
                    if (backup == null || (System.currentTimeMillis() - backup.created) / 1000 > config.backupInterval) {
                        try {
                            isBusy = true
                            server.broadcast(literalText("Running scheduled backup, please wait..."))
                            val (_, _, backId, totalSize, compressedSize, addedSize, millis) = service.createBackup(
                                server.getSavePath(WorldSavePath.ROOT).toAbsolutePath(),
                                "Scheduled backup"
                            ) { true }
                            val localBackup = File("x_backup.db.back")
                            localBackup.delete()
                            try {
                                (service.database.connector().connection as? SQLiteConnection)?.createStatement()
                                    ?.execute("VACUUM INTO '$localBackup';")
                            } catch (e: Exception) {
                                log.error("Error backing up database", e)
                            }
                            server.broadcast(
                                literalText(
                                    "Scheduled backup #$backId finished, ${sizeToString(totalSize)} " +
                                            "(${sizeToString(compressedSize)} after compression) " +
                                            "+${sizeToString(addedSize)} " +
                                            "in ${millis}ms"
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
