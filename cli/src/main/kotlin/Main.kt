@file:JvmName("Main")

import com.github.zly2006.xbackup.BackupDatabaseService
import com.github.zly2006.xbackup.Config
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.text.SimpleDateFormat
import kotlin.io.path.*

suspend fun main() {
    val pkg = BackupDatabaseService::class.java.`package`
    println("X Backup CLI Tools - ${pkg.implementationVersion}")

    if (!Path("level.dat").isRegularFile()) {
        println("Please run this command in the world directory")
        return
    }
    val worldRoot = Path("").absolute().normalize()

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

    var gameRoot = worldRoot
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

    val blobDir = gameRoot.resolve(config.blobPath).normalize()
    if (!blobDir.isDirectory()) {
        println("Fatal: Blob directory not found.")
        return
    }

    val service = BackupDatabaseService(worldRoot, database, blobDir, config)
    println("Recent 10 backups:")
    service.listBackups(0, 10).forEach {
        println("#${it.id} at ${
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(it.created)
        }: ${it.comment}")
    }

    while (true) {
        try {
            print("xb> ")
            val command = readlnOrNull() ?: break
            val parts = command.split(" ")
            when (parts[0]) {
                "?", "help" -> {
                    println(
                        """
                        Commands:
                        backup <comment> - Create a backup
                        list [page] [page_size] - List backups
                        restore <id> - Restore a backup
                        export <id> [path] - Export a backup
                        exit - Exit
                        """.trimIndent()
                    )
                }

                "backup" -> {
                    val comment = parts.drop(1).joinToString(" ")
                    service.createBackup(worldRoot, comment, false) { true }
                }

                "list" -> {
                    val page = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val count = parts.getOrNull(2)?.toIntOrNull() ?: 10
                    service.listBackups(page * count, count).forEach {
                        println(
                            "#${it.id} at ${
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(it.created)
                            }: ${it.comment}"
                        )
                    }
                    println("Total ${service.backupCount()} backups, use `list <page> <page_size>` to view more.")
                }

                "restore" -> {
                    val id = parts.getOrNull(1)?.toIntOrNull()
                    if (id == null) {
                        println("Usage: restore <id>")
                    }
                    else {
                        service.restore(id, worldRoot) { false }
                    }
                }

                "export" -> {
                    val id = parts.getOrNull(1)?.toIntOrNull()
                    val exportPath = gameRoot.resolve(parts.getOrNull(2) ?: "xb-export").createDirectories()
                    if (id == null) {
                        println("Usage: export <id> [path]")
                    }
                    else {
                        service.restore(id, exportPath) { false }
                    }
                }

                "exit" -> {
                    break
                }

                else -> {
                    println("Unknown command: $command")
                }
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    println("Goodbye!")
}
