package com.github.zly2006.xbackup

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import kotlinx.coroutines.launch
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import kotlin.io.path.Path

object XBackup : ModInitializer {
    lateinit var service: BackupDatabaseService
    private lateinit var server: MinecraftServer
    // Backup
    var backupRunning = false

    // Restore
    var reason = ""
    var blockPlayerJoin = false
    var disableWatchdog = false

    override fun onInitialize() {
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
            service = BackupDatabaseService(database, Path("blob"))
        }
    }

    fun ensureNotBusy(block: suspend () -> Unit) {
        require(server.isOnThread)
        if (backupRunning) {
            throw SimpleCommandExceptionType(Text.of("Backup is already running")).create()
        }
        backupRunning = true
        service.launch {
            try {
                block()
            } finally {
                backupRunning = false
            }
        }
    }
}
