import com.github.zly2006.xbackup.RegionFileMerger
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionFileMergerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun mergeKeepsChunksOutsideSelectionFromCurrentWorld() {
        val current = temp.newFile("current.mca").toPath()
        val backup = temp.newFile("backup.mca").toPath()
        val output = temp.newFile("output.mca").toPath()
        writeMca(current, mapOf((0 to 0) to byteArrayOf(1), (1 to 0) to byteArrayOf(2)))
        writeMca(backup, mapOf((0 to 0) to byteArrayOf(9), (1 to 0) to byteArrayOf(8)))

        RegionFileMerger.merge(
            current = current,
            backup = backup,
            output = output,
            regionX = 0,
            regionZ = 0,
            minChunkX = 0,
            maxChunkX = 0,
            minChunkZ = 0,
            maxChunkZ = 0,
        )

        assertContentEquals(byteArrayOf(9), readMcaChunk(output, 0, 0))
        assertContentEquals(byteArrayOf(2), readMcaChunk(output, 1, 0))
    }

    @Test
    fun missingBackupDeletesOnlySelectedChunks() {
        val current = temp.newFile("current-only.mca").toPath()
        val output = temp.newFile("output-delete.mca").toPath()
        writeMca(current, mapOf((0 to 0) to byteArrayOf(1), (1 to 0) to byteArrayOf(2)))

        RegionFileMerger.merge(
            current = current,
            backup = null,
            output = output,
            regionX = 0,
            regionZ = 0,
            minChunkX = 0,
            maxChunkX = 0,
            minChunkZ = 0,
            maxChunkZ = 0,
        )

        assertNull(readMcaChunk(output, 0, 0))
        assertContentEquals(byteArrayOf(2), readMcaChunk(output, 1, 0))
    }

    @Test
    fun missingCurrentWritesOnlySelectedBackupChunks() {
        val backup = temp.newFile("backup-only.mca").toPath()
        val output = temp.root.toPath().resolve("created.mca")
        writeMca(backup, mapOf((0 to 0) to byteArrayOf(9), (1 to 0) to byteArrayOf(8)))

        RegionFileMerger.merge(
            current = null,
            backup = backup,
            output = output,
            regionX = 0,
            regionZ = 0,
            minChunkX = 0,
            maxChunkX = 0,
            minChunkZ = 0,
            maxChunkZ = 0,
        )

        assertContentEquals(byteArrayOf(9), readMcaChunk(output, 0, 0))
        assertNull(readMcaChunk(output, 1, 0))
    }

    @Test
    fun emptyMergeDeletesOutputFile() {
        val current = temp.newFile("current-empty.mca").toPath()
        val output = temp.newFile("output-empty.mca").toPath()
        writeMca(current, mapOf((0 to 0) to byteArrayOf(1)))

        RegionFileMerger.merge(
            current = current,
            backup = null,
            output = output,
            regionX = 0,
            regionZ = 0,
            minChunkX = 0,
            maxChunkX = 0,
            minChunkZ = 0,
            maxChunkZ = 0,
        )

        assertFalse(output.exists())
    }

    @Test
    fun reportsRestoreAndRemoveActions() {
        val current = temp.newFile("current-log.mca").toPath()
        val backup = temp.newFile("backup-log.mca").toPath()
        val output = temp.newFile("output-log.mca").toPath()
        writeMca(current, mapOf((0 to 0) to byteArrayOf(1), (2 to 0) to byteArrayOf(3)))
        writeMca(backup, mapOf((0 to 0) to byteArrayOf(9)))
        val actions = mutableListOf<Triple<Int, Int, RegionFileMerger.ChunkAction>>()

        RegionFileMerger.merge(
            current = current,
            backup = backup,
            output = output,
            regionX = 0,
            regionZ = 0,
            minChunkX = 0,
            maxChunkX = 2,
            minChunkZ = 0,
            maxChunkZ = 0,
        ) { x, z, action ->
            actions += Triple(x, z, action)
        }

        assertEquals(
            listOf(
                Triple(0, 0, RegionFileMerger.ChunkAction.RESTORED),
                Triple(2, 0, RegionFileMerger.ChunkAction.REMOVED),
            ),
            actions
        )
    }

    @Test
    fun mergeFromBytesRestoresMultipleSelectedChunksInOnePass() {
        val current = temp.newFile("current-bytes.mca").toPath()
        val backup = temp.newFile("backup-bytes.mca").toPath()
        val output = temp.newFile("output-bytes.mca").toPath()
        writeMca(current, mapOf((0 to 0) to byteArrayOf(1), (1 to 0) to byteArrayOf(2), (3 to 0) to byteArrayOf(4)))
        writeMca(backup, mapOf((0 to 0) to byteArrayOf(9), (1 to 0) to byteArrayOf(8)))
        val actions = mutableListOf<Triple<Int, Int, RegionFileMerger.ChunkAction>>()

        RegionFileMerger.merge(
            current = current,
            backupBytes = backup.readBytes(),
            output = output,
            regionX = 0,
            regionZ = 0,
            minChunkX = 0,
            maxChunkX = 1,
            minChunkZ = 0,
            maxChunkZ = 0,
        ) { x, z, action ->
            actions += Triple(x, z, action)
        }

        assertContentEquals(byteArrayOf(9), readMcaChunk(output, 0, 0))
        assertContentEquals(byteArrayOf(8), readMcaChunk(output, 1, 0))
        assertContentEquals(byteArrayOf(4), readMcaChunk(output, 3, 0))
        assertEquals(
            listOf(
                Triple(0, 0, RegionFileMerger.ChunkAction.RESTORED),
                Triple(1, 0, RegionFileMerger.ChunkAction.RESTORED),
            ),
            actions
        )
    }

    @Test
    fun truncatedBackupDoesNotModifyWorldFile() {
        val current = temp.newFile("current-safe.mca").toPath()
        val output = temp.newFile("output-safe.mca").toPath()
        writeMca(current, mapOf((0 to 0) to byteArrayOf(1), (1 to 0) to byteArrayOf(2)))
        output.writeBytes(current.readBytes())
        val before = output.readBytes()

        assertFailsWith<RegionFileMerger.UnreadableRegionFileException> {
            RegionFileMerger.merge(
                current = current,
                backupBytes = ByteArray(100),
                output = output,
                regionX = 0,
                regionZ = 0,
                minChunkX = 0,
                maxChunkX = 0,
                minChunkZ = 0,
                maxChunkZ = 0,
            )
        }

        assertContentEquals(before, output.readBytes())
        assertContentEquals(byteArrayOf(1), readMcaChunk(output, 0, 0))
    }

    @Test
    fun headerPointingPastEofIsRejected() {
        val bytes = ByteArray(8192)
        bytes[2] = 3
        bytes[3] = 1
        assertFalse(RegionFileMerger.isIntactMca(bytes))
    }

    @Test
    fun emptyHeaderOnlyMcaIsIntact() {
        assertTrue(RegionFileMerger.isIntactMca(ByteArray(8192)))
    }

    @Test
    fun invalidCommitKeepsOriginalAndDeletesTemp() {
        val output = temp.newFile("commit-safe.mca").toPath()
        writeMca(output, mapOf((0 to 0) to byteArrayOf(1)))
        val before = output.readBytes()

        assertFailsWith<RegionFileMerger.UnreadableRegionFileException> {
            RegionFileMerger.commitReplacing(output, ByteArray(100))
        }

        assertContentEquals(before, output.readBytes())
        assertFalse(output.resolveSibling("${output.fileName}.xb-new").exists())
    }

    @Test
    fun mergeReplacesViaTempThenAtomicMove() {
        val current = temp.newFile("current-commit.mca").toPath()
        val backup = temp.newFile("backup-commit.mca").toPath()
        val output = temp.newFile("output-commit.mca").toPath()
        writeMca(current, mapOf((0 to 0) to byteArrayOf(1)))
        writeMca(backup, mapOf((0 to 0) to byteArrayOf(9)))

        RegionFileMerger.merge(
            current = current,
            backup = backup,
            output = output,
            regionX = 0,
            regionZ = 0,
            minChunkX = 0,
            maxChunkX = 0,
            minChunkZ = 0,
            maxChunkZ = 0,
        )

        assertContentEquals(byteArrayOf(9), readMcaChunk(output, 0, 0))
        assertFalse(output.resolveSibling("${output.fileName}.xb-new").exists())
        assertTrue(RegionFileMerger.isIntactMca(output.readBytes()))
    }

    @Test
    fun replaceCommittedFileSwapsTempOverOutput() {
        val output = temp.newFile("replace-out.mca").toPath()
        val tmp = output.resolveSibling("${output.fileName}.xb-new")
        output.writeBytes(byteArrayOf(1, 2, 3))
        tmp.writeBytes(byteArrayOf(9, 8, 7))

        RegionFileMerger.replaceCommittedFile(tmp, output)

        assertContentEquals(byteArrayOf(9, 8, 7), output.readBytes())
        assertFalse(tmp.exists())
    }

    @Test
    fun combineChunkActionPrefersRestore() {
        assertEquals(
            RegionFileMerger.ChunkAction.RESTORED,
            RegionFileMerger.combineAction(RegionFileMerger.ChunkAction.REMOVED, RegionFileMerger.ChunkAction.RESTORED)
        )
        assertEquals(
            RegionFileMerger.ChunkAction.REMOVED,
            RegionFileMerger.combineAction(null, RegionFileMerger.ChunkAction.REMOVED)
        )
    }

    private fun writeMca(file: Path, chunks: Map<Pair<Int, Int>, ByteArray>) {
        val locations = ByteArray(4096)
        val timestamps = ByteArray(4096)
        val data = ArrayList<ByteArray>()
        var sector = 2
        for (lz in 0..31) {
            for (lx in 0..31) {
                val payload = chunks[lx to lz] ?: continue
                val index = lx + lz * 32
                val record = encodeChunkRecord(payload)
                val sectorCount = (record.size + 4095) / 4096
                val padded = record + ByteArray(sectorCount * 4096 - record.size)
                val loc = (sector shl 8) or sectorCount
                locations[index * 4] = (loc ushr 24).toByte()
                locations[index * 4 + 1] = (loc ushr 16).toByte()
                locations[index * 4 + 2] = (loc ushr 8).toByte()
                locations[index * 4 + 3] = loc.toByte()
                data.add(padded)
                sector += sectorCount
            }
        }
        file.writeBytes(locations + timestamps + data.fold(ByteArray(0)) { acc, bytes -> acc + bytes })
    }

    private fun encodeChunkRecord(payload: ByteArray): ByteArray {
        val length = 1 + payload.size
        return ByteBuffer.allocate(4 + length).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(length)
            put(3)
            put(payload)
        }.array()
    }

    private fun readMcaChunk(file: Path, localX: Int, localZ: Int): ByteArray? {
        val bytes = file.readBytes()
        val index = localX + localZ * 32
        val loc = ByteBuffer.wrap(bytes, index * 4, 4).order(ByteOrder.BIG_ENDIAN).int
        val offset = loc ushr 8
        if (offset == 0) return null
        val pos = offset * 4096
        val length = ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int
        return bytes.copyOfRange(pos + 5, pos + 4 + length)
    }
}
