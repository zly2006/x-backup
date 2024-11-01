package com.github.zly2006.xbackup

import RestartUtils
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
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

object XBackup : ModInitializer {
    val log = LoggerFactory.getLogger("XBackup")
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

    override fun onInitialize() {
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
        ServerLifecycleEvents.SERVER_STARTED.register {
            this.server = it
            val path = it.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
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
