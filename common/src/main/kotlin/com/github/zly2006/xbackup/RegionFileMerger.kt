package com.github.zly2006.xbackup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

object RegionFileMerger {
    class UnreadableRegionFileException(message: String) : IllegalStateException(message)

    enum class ChunkAction {
        RESTORED,
        REMOVED,
    }

    fun merge(
        current: Path?,
        backup: Path?,
        output: Path,
        regionX: Int,
        regionZ: Int,
        minChunkX: Int,
        maxChunkX: Int,
        minChunkZ: Int,
        maxChunkZ: Int,
        onChunk: (chunkX: Int, chunkZ: Int, action: ChunkAction) -> Unit = { _, _, _ -> },
    ) {
        merge(
            current = current,
            backupBytes = backup?.takeIf { it.exists() }?.readBytes(),
            output = output,
            regionX = regionX,
            regionZ = regionZ,
            minChunkX = minChunkX,
            maxChunkX = maxChunkX,
            minChunkZ = minChunkZ,
            maxChunkZ = maxChunkZ,
            onChunk = onChunk,
        )
    }

    fun merge(
        current: Path?,
        backupBytes: ByteArray?,
        output: Path,
        regionX: Int,
        regionZ: Int,
        minChunkX: Int,
        maxChunkX: Int,
        minChunkZ: Int,
        maxChunkZ: Int,
        onChunk: (chunkX: Int, chunkZ: Int, action: ChunkAction) -> Unit = { _, _, _ -> },
    ) {
        val currentBytes = current?.takeIf { it.exists() }?.readBytes()
        if (currentBytes != null && !isIntactMca(currentBytes)) {
            failValidation("Current MCA is truncated or corrupt: $current")
        }
        if (backupBytes != null && !isIntactMca(backupBytes)) {
            failValidation("Backup MCA is truncated or corrupt")
        }
        val selected = selectedIndices(regionX, regionZ, minChunkX, maxChunkX, minChunkZ, maxChunkZ)
        val currentChunks = currentBytes?.let { readChunks(it) } ?: emptyMap()
        val backupChunks = backupBytes?.let { readChunks(it, selected) } ?: emptyMap()
        val result = currentChunks.toMutableMap()

        for (index in selected) {
            val localX = index % 32
            val localZ = index / 32
            val chunkX = regionX * 32 + localX
            val chunkZ = regionZ * 32 + localZ
            val backupData = backupChunks[index]
            if (backupData != null) {
                result[index] = backupData
                onChunk(chunkX, chunkZ, ChunkAction.RESTORED)
            } else if (index in result) {
                result.remove(index)
                onChunk(chunkX, chunkZ, ChunkAction.REMOVED)
            }
        }

        if (result.isEmpty()) {
            output.deleteIfExists()
            return
        }
        writeAndReplaceMca(output, result)
    }

    fun commitReplacing(output: Path, bytes: ByteArray) {
        commitTemp(output) { tmp -> tmp.writeBytes(bytes) }
    }

    internal fun writeAndReplaceMca(output: Path, chunks: Map<Int, ByteArray>) {
        commitTemp(output) { tmp -> writeAllChunks(tmp, chunks) }
    }

    private fun commitTemp(output: Path, write: (Path) -> Unit) {
        output.parent?.createDirectories()
        val tmp = output.resolveSibling("${output.fileName}.xb-new")
        try {
            write(tmp)
            FileChannel.open(tmp, StandardOpenOption.WRITE).use { it.force(true) }
            if (!isIntactMca(tmp.readBytes())) {
                failValidation("Written temp MCA failed validation: $tmp")
            }
            replaceCommittedFile(tmp, output)
        } catch (e: Throwable) {
            if (output.exists()) {
                tmp.deleteIfExists()
            }
            throw e
        }
    }

    fun replaceCommittedFile(tmp: Path, output: Path) {
        try {
            tmp.moveTo(output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            log.warn("[X Backup] Atomic move not supported for {}, falling back to delete+rename", output, e)
            output.deleteIfExists()
            tmp.moveTo(output)
        }
    }

    private fun failValidation(message: String): Nothing {
        val full = "[X Backup] $message"
        log.error(full)
        throw UnreadableRegionFileException(full)
    }

    fun isIntactMca(bytes: ByteArray): Boolean {
        if (bytes.size < 8192 || bytes.size % 4096 != 0) {
            return false
        }
        for (index in 0 until 1024) {
            val loc = ByteBuffer.wrap(bytes, index * 4, 4).order(ByteOrder.BIG_ENDIAN).int
            val offset = loc ushr 8
            val count = loc and 0xFF
            if (offset == 0 || count == 0) continue
            val start = offset * 4096
            val end = (offset + count) * 4096
            if (start < 8192 || end > bytes.size) return false
            val length = ByteBuffer.wrap(bytes, start, 4).order(ByteOrder.BIG_ENDIAN).int
            if (length <= 0 || start + 4 + length > bytes.size) return false
        }
        return true
    }

    fun combineAction(current: ChunkAction?, incoming: ChunkAction): ChunkAction {
        return if (incoming == ChunkAction.RESTORED) incoming else current ?: incoming
    }

    internal fun selectedIndices(
        regionX: Int,
        regionZ: Int,
        minChunkX: Int,
        maxChunkX: Int,
        minChunkZ: Int,
        maxChunkZ: Int,
    ): Set<Int> {
        val indices = LinkedHashSet<Int>()
        val minLocalX = (minChunkX - regionX * 32).coerceAtLeast(0)
        val maxLocalX = (maxChunkX - regionX * 32).coerceAtMost(31)
        val minLocalZ = (minChunkZ - regionZ * 32).coerceAtLeast(0)
        val maxLocalZ = (maxChunkZ - regionZ * 32).coerceAtMost(31)
        if (minLocalX > maxLocalX || minLocalZ > maxLocalZ) return emptySet()
        for (localZ in minLocalZ..maxLocalZ) {
            for (localX in minLocalX..maxLocalX) {
                val chunkX = regionX * 32 + localX
                val chunkZ = regionZ * 32 + localZ
                if (chunkX in minChunkX..maxChunkX && chunkZ in minChunkZ..maxChunkZ) {
                    indices += localX + localZ * 32
                }
            }
        }
        return indices
    }

    internal fun readAllChunks(file: Path): Map<Int, ByteArray> {
        return readChunks(file.readBytes())
    }

    internal fun readChunks(bytes: ByteArray, indices: Set<Int>? = null): Map<Int, ByteArray> {
        if (bytes.size < 8192) {
            return emptyMap()
        }
        val chunks = mutableMapOf<Int, ByteArray>()
        val toRead: Iterable<Int> = indices ?: (0 until 1024)
        for (index in toRead) {
            if (index !in 0 until 1024) continue
            val loc = ByteBuffer.wrap(bytes, index * 4, 4).order(ByteOrder.BIG_ENDIAN).int
            val offset = loc ushr 8
            val count = loc and 0xFF
            if (offset == 0 || count == 0) continue
            val pos = offset * 4096
            if (pos + 5 > bytes.size) continue
            val length = ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            if (length <= 0 || pos + 4 + length > bytes.size) continue
            chunks[index] = bytes.copyOfRange(pos, pos + 4 + length)
        }
        return chunks
    }

    internal fun writeAllChunks(file: Path, chunks: Map<Int, ByteArray>) {
        val locations = ByteArray(4096)
        val data = ArrayList<ByteArray>()
        var sector = 2
        for (index in 0 until 1024) {
            val record = chunks[index] ?: continue
            val sectorCount = (record.size + 4095) / 4096
            val padded = if (record.size % 4096 == 0) {
                record
            } else {
                record + ByteArray(sectorCount * 4096 - record.size)
            }
            val loc = (sector shl 8) or sectorCount
            locations[index * 4] = (loc ushr 24).toByte()
            locations[index * 4 + 1] = (loc ushr 16).toByte()
            locations[index * 4 + 2] = (loc ushr 8).toByte()
            locations[index * 4 + 3] = loc.toByte()
            data.add(padded)
            sector += sectorCount
        }
        val out = ByteArray(8192 + data.sumOf { it.size })
        System.arraycopy(locations, 0, out, 0, 4096)
        var offset = 8192
        for (chunk in data) {
            System.arraycopy(chunk, 0, out, offset, chunk.size)
            offset += chunk.size
        }
        file.writeBytes(out)
    }
}
