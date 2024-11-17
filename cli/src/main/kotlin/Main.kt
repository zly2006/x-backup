@file:JvmName("Main")

import com.github.zly2006.xbackup.BackupDatabaseService
import com.github.zly2006.xbackup.Config
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.time.Instant
import kotlin.io.path.*

fun main() {
    if (!Path("level.dat").isRegularFile()) {
        println("Please run this command in the world directory")
        return
    }

    val database = Database.connect(
        SQLiteDataSource(
            SQLiteConfig().apply {
                enforceForeignKeys(true)
                setCacheSize(100_000)
                setJournalMode(SQLiteConfig.JournalMode.WAL)
            }
        ).apply {
            url = "jdbc:sqlite:x_backup.db"
        }
    )

    var gameRoot = Path(".").absolute()
    while (!gameRoot.resolve("config").isDirectory()) {
        gameRoot = gameRoot.parent
    }

    val configFile = gameRoot.resolve("config/x-backup.config.json")
    var config = Config()
    if (configFile.isRegularFile()) {
        runCatching {
            config = Json.decodeFromString(configFile.readText())
            println("Config file loaded.")
        }
    } else {
        println("Warning: Config file not found, using default config")
    }

    val blobDir = gameRoot.resolve(config.blobPath)
    if (!blobDir.isDirectory()) {
        println("Fatal: Blob directory not found.")
        return
    }

    val service = BackupDatabaseService(database, blobDir)
    println("Recent 10 backups:")
    service.listBackups(0, 10).forEach {
        println("Backup ${it.id} at ${Instant.ofEpochMilli(it.created)}: ${it.comment}")
    }

    println("Goodbye!")
}
