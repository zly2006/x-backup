package com.github.zly2006.xbackup

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.max
import kotlin.math.min

enum class RegionalRestoreMode {
    MCA,
    CHUNK,
}

object RegionalRestore {
    data class Range(
        val minX: Int,
        val minZ: Int,
        val maxX: Int,
        val maxZ: Int,
    ) {
        fun contains(x: Int, z: Int) = x in minX..maxX && z in minZ..maxZ
    }

    fun resolveRange(
        mode: RegionalRestoreMode,
        useBlockCoords: Boolean,
        x1: Int,
        z1: Int,
        x2: Int,
        z2: Int,
    ): Range {
        val minX = min(x1, x2)
        val maxX = max(x1, x2)
        val minZ = min(z1, z2)
        val maxZ = max(z1, z2)
        if (!useBlockCoords) {
            return Range(minX, minZ, maxX, maxZ)
        }
        return when (mode) {
            RegionalRestoreMode.MCA -> Range(minX shr 9, minZ shr 9, maxX shr 9, maxZ shr 9)
            RegionalRestoreMode.CHUNK -> Range(minX shr 4, minZ shr 4, maxX shr 4, maxZ shr 4)
        }
    }

    fun isPathInWorldSave(file: Path, worldSaveDir: Path, isOverworld: Boolean): Boolean {
        val normalized = file.normalize()
        val worldDir = worldSaveDir.normalize()
        if (!normalized.startsWith(worldDir)) {
            return false
        }
        if (isOverworld) {
            if (normalized.startsWith(worldDir.resolve("DIM-1"))) return false
            if (normalized.startsWith(worldDir.resolve("DIM1"))) return false
            if (normalized.startsWith(worldDir.resolve("dimensions"))) return false
        }
        return true
    }

    fun parseRegionCoords(fileName: String): Pair<Int, Int>? {
        val parts = fileName.split(".")
        if (parts.size != 4) return null
        if (parts[0] != "r" && parts[0] != "c") return null
        if (parts[3] != "mca" && parts[3] != "mcc") return null
        val x = parts[1].toIntOrNull() ?: return null
        val z = parts[2].toIntOrNull() ?: return null
        return x to z
    }

    fun isRegionStorageFile(relative: Path): Boolean {
        val parent = relative.normalize().parent?.fileName?.toString() ?: return false
        return parent in REGION_DIRS
    }

    fun entryPath(relative: String): Path = Path(relative).normalize()

    data class McaLogPlan(
        val restore: List<Pair<Int, Int>>,
        val delete: List<Pair<Int, Int>>,
    )

    fun regionFileName(regionX: Int, regionZ: Int): String = "r.$regionX.$regionZ.mca"

    fun formatUnitLog(unit: String, x: Int, z: Int, restoring: Boolean): String {
        val verb = if (restoring) "Restoring" else "Removing"
        return "[X Backup] $verb $unit ($x, $z)"
    }

    fun uniqueRegionCoords(paths: Iterable<String>): List<Pair<Int, Int>> {
        val result = LinkedHashSet<Pair<Int, Int>>()
        for (path in paths) {
            val name = path.substringAfterLast('/').substringAfterLast('\\')
            if (!name.endsWith(".mca")) continue
            parseRegionCoords(name)?.let { result += it }
        }
        return result.toList()
    }

    fun planMcaLogs(
        backupMcaPaths: Iterable<String>,
        worldMcaPaths: Iterable<String>,
        range: Range,
    ): McaLogPlan {
        val restore = uniqueRegionCoords(backupMcaPaths).filter { range.contains(it.first, it.second) }
        val restoreSet = restore.toSet()
        val delete = uniqueRegionCoords(worldMcaPaths)
            .filter { range.contains(it.first, it.second) && it !in restoreSet }
        return McaLogPlan(restore, delete)
    }

    val REGION_DIRS = setOf("region", "entities", "poi")
}
