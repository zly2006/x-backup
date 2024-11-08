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
import org.sqlite.SQLiteDataSource
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.readText
import kotlin.io.path.writeText

object XBackup : ModInitializer {
    lateinit var config: Config
    private val configPath = FabricLoader.getInstance().configDir.resolve("x-backup.config.json")
    val log = LoggerFactory.getLogger("XBackup")!!
    lateinit var service: BackupDatabaseService
    lateinit var server: MinecraftServer
    val serverStarted get() = ::server.isInitialized
    // Backup
    var isBusy = false

    // Restore
    var reason = ""
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
        loadConfig()
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
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            this.server = server
            val path = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
            val database = Database.connect(
                SQLiteDataSource(
                    SQLiteConfig().apply {
                        enforceForeignKeys(true)
                        setCacheSize(100_000)
                        setJournalMode(SQLiteConfig.JournalMode.WAL)
                    }
                ).apply {
                    url = "jdbc:sqlite:$path/x_backup.db"
                }
            )
            service = BackupDatabaseService(database, Path("blob").absolute())
            crontabJob = GlobalScope.launch {
                while (true) {
                    delay(10000)
                    val backup = service.getLatestBackup()
                    if ((System.currentTimeMillis() - backup.created) / 1000 > config.backupInterval && !isBusy) {
                        try {
                            isBusy = true
                            server.broadcast(literalText("Running scheduled backup, please wait..."))
                            val (_, _, backId, totalSize, compressedSize, addedSize, millis) = service.createBackup(
                                server.getSavePath(WorldSavePath.ROOT).toAbsolutePath(),
                                "Scheduled backup"
                            ) { true }
                            server.broadcast(
                                literalText(
                                    "Backup #$backId finished, ${sizeToString(totalSize)} " +
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
