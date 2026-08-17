package com.github.zly2006.xbackup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

object RegionFileMerger {
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
        val currentChunks = current?.takeIf { it.exists() }?.let { readAllChunks(it) } ?: emptyMap()
        val backupChunks = backup?.takeIf { it.exists() }?.let { readAllChunks(it) } ?: emptyMap()
        val result = currentChunks.toMutableMap()

        for (localZ in 0..31) {
            for (localX in 0..31) {
                val chunkX = regionX * 32 + localX
                val chunkZ = regionZ * 32 + localZ
                if (chunkX !in minChunkX..maxChunkX || chunkZ !in minChunkZ..maxChunkZ) {
                    continue
                }
                val index = localX + localZ * 32
                val backupData = backupChunks[index]
                if (backupData != null) {
                    result[index] = backupData
                    onChunk(chunkX, chunkZ, ChunkAction.RESTORED)
                } else if (index in result) {
                    result.remove(index)
                    onChunk(chunkX, chunkZ, ChunkAction.REMOVED)
                }
            }
        }

        if (result.isEmpty()) {
            output.deleteIfExists()
            return
        }
        output.parent?.createDirectories()
        val tmp = output.resolveSibling("${output.fileName}.tmp")
        writeAllChunks(tmp, result)
        tmp.moveTo(output, StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun readAllChunks(file: Path): Map<Int, ByteArray> {
        val bytes = file.readBytes()
        if (bytes.size < 8192) {
            return emptyMap()
        }
        val chunks = mutableMapOf<Int, ByteArray>()
        for (index in 0 until 1024) {
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
