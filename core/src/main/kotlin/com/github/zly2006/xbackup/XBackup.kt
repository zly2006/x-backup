package com.github.zly2006.xbackup

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.util.WorldSavePath
import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource

object XBackup : ModInitializer {

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register {
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
            BackupDatabaseService(database, path)
        }
    }
}
