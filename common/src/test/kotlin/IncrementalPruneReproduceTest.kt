import com.github.zly2006.xbackup.BackupDatabaseService
import com.github.zly2006.xbackup.BackupDatabaseService.Companion.EMPTY_BLOB_HASH
import com.github.zly2006.xbackup.Config
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncrementalPruneReproduceTest {
    @Test
    fun backfillsBlobRefsWhenTableMissing() = runBlocking {
        val env = setupService()
        val world = env.world
        world.resolve("level.dat").writeBytes(ByteArray(2048) { 1 })
        world.resolve("poi/empty.mca").createParentDirectories().writeBytes(ByteArray(0))
        setStableMtime(world)
        val created = env.service.createBackup(world, "full")
        val backup = env.service.getBackupInternal(created.backId)!!
        val contentHash = backup.entries.first { it.path == "level.dat" }.hash

        transaction(env.database) {
            exec("DROP TABLE blob_refs")
        }
        env.service.close()
        val database = connectDatabase(env.tmp)
        val reopened = BackupDatabaseService(
            env.world,
            database,
            env.blob,
            Config()
        )
        assertEquals(1, reopened.blobRefCount(contentHash))
        assertNull(reopened.blobRefCount(EMPTY_BLOB_HASH))
        reopened.close()
    }

    @Test
    fun trustsExistingBlobRefsOnStartup() = runBlocking {
        val env = setupService()
        val world = env.world
        world.resolve("level.dat").writeBytes(ByteArray(2048) { 1 })
        setStableMtime(world)
        val created = env.service.createBackup(world, "full")
        val hash = env.service.getBackupInternal(created.backId)!!.entries.first { !it.isDirectory }.hash
        assertEquals(1, env.service.blobRefCount(hash))

        transaction(env.database) {
            exec("UPDATE blob_refs SET ref_count = 999 WHERE hash = '$hash'")
        }
        val reopened = BackupDatabaseService(env.world, env.database, env.blob, Config())
        assertEquals(999, reopened.blobRefCount(hash))
        reopened.close()
    }

    @Test
    fun unchangedPathIncrementalsSurviveDeletingFirstBackup() = runBlocking {
        val env = setupService()
        val world = env.world
        val service = env.service

        world.resolve("level.dat").writeBytes(ByteArray(2048) { 1 })
        world.resolve("region/r.0.0.mca").createParentDirectories()
            .writeBytes(ByteArray(4096) { 2 })
        world.resolve("dup_a.txt").writeText("shared-content")
        world.resolve("dup_b.txt").writeText("shared-content")
        setStableMtime(world)

        val full = service.createBackup(world, "full")
        world.resolve("region/r.0.0.mca").writeBytes(ByteArray(4096) { 3 })
        setStableMtime(world)
        val inc1 = service.createBackup(world, "inc1")
        world.resolve("level.dat").writeBytes(ByteArray(2048) { 4 })
        setStableMtime(world)
        val inc2 = service.createBackup(world, "inc2")

        service.deleteBackupInternal(service.getBackupInternal(full.backId)!!)
        val valid1 = service.check(service.getBackupInternal(inc1.backId)!!)
        val valid2 = service.check(service.getBackupInternal(inc2.backId)!!)
        assertTrue(valid1 && valid2, "unchanged-path incrementals should stay valid after deleting first backup")
    }

    @Test
    fun emptyPoiEntitiesNeverEnterBlobRefs() = runBlocking {
        val env = setupService()
        val world = env.world
        val service = env.service

        world.resolve("level.dat").writeBytes(ByteArray(2048) { 1 })
        world.resolve("poi/r.-29.-26.mca").createParentDirectories().writeBytes(ByteArray(0))
        world.resolve("poi/r.-42.-23.mca").writeBytes(ByteArray(0))
        world.resolve("entities/r.-51.41.mca").createParentDirectories().writeBytes(ByteArray(0))
        setStableMtime(world)

        val full = service.createBackup(world, "full")
        assertNull(service.blobRefCount(EMPTY_BLOB_HASH))
        assertTrue(service.getBlobFile(EMPTY_BLOB_HASH).exists())

        world.resolve("poi/r.-29.-26.mca").writeBytes(ByteArray(4096) { 9 })
        setStableMtime(world)
        val inc = service.createBackup(world, "inc-poi-filled")

        service.deleteBackupInternal(service.getBackupInternal(full.backId)!!)
        assertTrue(service.check(service.getBackupInternal(inc.backId)!!))
        assertTrue(service.getBlobFile(EMPTY_BLOB_HASH).exists())
        assertNull(service.blobRefCount(EMPTY_BLOB_HASH))
    }

    @Test
    fun renamedFileKeepsSharedHashBlob() = runBlocking {
        val env = setupService()
        val world = env.world
        val service = env.service

        val payload = ByteArray(2048) { 7 }
        world.resolve("old_name.dat").writeBytes(payload)
        setStableMtime(world)

        val full = service.createBackup(world, "full")
        val hash = service.getBackupInternal(full.backId)!!.entries.first { it.path == "old_name.dat" }.hash
        assertEquals(1, service.blobRefCount(hash))

        world.resolve("old_name.dat").toFile().delete()
        world.resolve("new_name.dat").writeBytes(payload)
        setStableMtime(world)
        val inc = service.createBackup(world, "inc-renamed")
        assertEquals(2, service.blobRefCount(hash))

        service.deleteBackupInternal(service.getBackupInternal(full.backId)!!)
        assertEquals(1, service.blobRefCount(hash))
        assertTrue(service.check(service.getBackupInternal(inc.backId)!!))
        assertTrue(service.getBlobFile(hash).exists())
    }

    @Test
    fun restoreRecreatesMissingEmptyBlob() = runBlocking {
        val env = setupService()
        val world = env.world
        val service = env.service
        world.resolve("level.dat").writeBytes(ByteArray(2048) { 1 })
        world.resolve("poi/empty.mca").createParentDirectories().writeBytes(ByteArray(0))
        setStableMtime(world)
        val created = service.createBackup(world, "full")

        service.getBlobFile(EMPTY_BLOB_HASH).toFile().delete()
        assertTrue(!service.getBlobFile(EMPTY_BLOB_HASH).exists())

        val restoreDir = env.tmp.resolve("restore")
        Files.createDirectories(restoreDir)
        service.restore(created.backId, restoreDir) { false }

        assertTrue(service.getBlobFile(EMPTY_BLOB_HASH).exists())
        assertEquals(0, restoreDir.resolve("poi/empty.mca").fileSize())
    }

    @Test
    fun deleteUnusedBlobsSkipsEmptyHash() = runBlocking {
        val env = setupService()
        val world = env.world
        val service = env.service
        world.resolve("level.dat").writeBytes(ByteArray(2048) { 1 })
        world.resolve("poi/empty.mca").createParentDirectories().writeBytes(ByteArray(0))
        setStableMtime(world)
        service.createBackup(world, "full")
        assertTrue(service.getBlobFile(EMPTY_BLOB_HASH).exists())

        service.deleteUnusedBlobs()
        assertTrue(service.getBlobFile(EMPTY_BLOB_HASH).exists())
    }

    private fun setupService(): Env {
        val tmp = Files.createTempDirectory("xb-prune")
        val world = tmp.resolve("world")
        val blob = tmp.resolve("blob")
        Files.createDirectories(world)
        Files.createDirectories(blob)
        val database = connectDatabase(tmp)
        return Env(tmp, world, blob.normalize().toAbsolutePath(), database, BackupDatabaseService(world, database, blob.normalize().toAbsolutePath(), Config()))
    }

    private fun connectDatabase(tmp: java.nio.file.Path): Database {
        return Database.connect(
            SQLiteDataSource(
                SQLiteConfig().apply { enforceForeignKeys(true) }
            ).apply {
                url = "jdbc:sqlite:${tmp.resolve("x_backup.db")}"
            }
        )
    }

    private fun setStableMtime(world: java.nio.file.Path) {
        val t = System.currentTimeMillis()
        val stable = if (t % 1000L == 0L) t + 1 else t
        world.toFile().walk().filter { it.isFile }.forEach { it.setLastModified(stable) }
    }

    private data class Env(
        val tmp: java.nio.file.Path,
        val world: java.nio.file.Path,
        val blob: java.nio.file.Path,
        val database: Database,
        val service: BackupDatabaseService,
    )
}
