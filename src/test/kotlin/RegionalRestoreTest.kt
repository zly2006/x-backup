import com.github.zly2006.xbackup.RegionalRestore
import com.github.zly2006.xbackup.RegionalRestoreMode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionalRestoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun mcaCoordsAreUsedAsRegionIndices() {
        val range = RegionalRestore.resolveRange(RegionalRestoreMode.MCA, false, 0, 0, 1, 1)
        assertEquals(RegionalRestore.Range(0, 0, 1, 1), range)
    }

    @Test
    fun chunkCoordsAreUsedAsChunkIndices() {
        val range = RegionalRestore.resolveRange(RegionalRestoreMode.CHUNK, false, 0, 0, 10, 10)
        assertEquals(RegionalRestore.Range(0, 0, 10, 10), range)
    }

    @Test
    fun blockCoordsConvertToMcaRegion() {
        val small = RegionalRestore.resolveRange(RegionalRestoreMode.MCA, true, 0, 0, 10, 10)
        assertEquals(RegionalRestore.Range(0, 0, 0, 0), small)

        val crossed = RegionalRestore.resolveRange(RegionalRestoreMode.MCA, true, 0, 0, 512, 512)
        assertEquals(RegionalRestore.Range(0, 0, 1, 1), crossed)
    }

    @Test
    fun blockCoordsConvertToChunks() {
        val small = RegionalRestore.resolveRange(RegionalRestoreMode.CHUNK, true, 0, 0, 10, 10)
        assertEquals(RegionalRestore.Range(0, 0, 0, 0), small)

        val crossed = RegionalRestore.resolveRange(RegionalRestoreMode.CHUNK, true, 0, 0, 16, 16)
        assertEquals(RegionalRestore.Range(0, 0, 1, 1), crossed)
    }

    @Test
    fun resolveRangeNormalizesMinMaxOrder() {
        val range = RegionalRestore.resolveRange(RegionalRestoreMode.CHUNK, false, 10, 8, 0, 2)
        assertEquals(RegionalRestore.Range(0, 2, 10, 8), range)
    }

    @Test
    fun overworldExcludesOtherDimensions() {
        val root = temp.newFolder("world").toPath().toAbsolutePath().normalize()
        assertTrue(RegionalRestore.isPathInWorldSave(root.resolve("region/r.0.0.mca"), root, true))
        assertFalse(RegionalRestore.isPathInWorldSave(root.resolve("DIM-1/region/r.0.0.mca"), root, true))
        assertFalse(RegionalRestore.isPathInWorldSave(root.resolve("DIM1/region/r.0.0.mca"), root, true))
        assertFalse(RegionalRestore.isPathInWorldSave(root.resolve("dimensions/mod/custom/region/r.0.0.mca"), root, true))
    }

    @Test
    fun netherOnlyMatchesItsOwnSaveDirectory() {
        val root = temp.newFolder("world2").toPath().toAbsolutePath().normalize()
        val nether = root.resolve("DIM-1")
        assertTrue(RegionalRestore.isPathInWorldSave(nether.resolve("region/r.0.0.mca"), nether, false))
        assertFalse(RegionalRestore.isPathInWorldSave(root.resolve("region/r.0.0.mca"), nether, false))
    }

    @Test
    fun parseRegionFileNameSupportsNegatives() {
        assertEquals(-1 to -2, RegionalRestore.parseRegionCoords("r.-1.-2.mca"))
        assertEquals(3 to 4, RegionalRestore.parseRegionCoords("c.3.4.mcc"))
        assertEquals(null, RegionalRestore.parseRegionCoords("level.dat"))
    }

    @Test
    fun planMcaLogsDeduplicatesRegionEntitiesAndPoi() {
        val range = RegionalRestore.Range(0, 0, 1, 0)
        val plan = RegionalRestore.planMcaLogs(
            backupMcaPaths = listOf(
                "region/r.0.0.mca",
                "entities/r.0.0.mca",
                "poi/r.0.0.mca",
                "region/r.1.0.mca",
            ),
            worldMcaPaths = listOf(
                "region/r.0.0.mca",
                "entities/r.2.0.mca",
                "poi/r.2.0.mca",
                "region/r.1.0.mca",
            ),
            range = range,
        )
        assertEquals(listOf(0 to 0, 1 to 0), plan.restore)
        assertEquals(emptyList(), plan.delete)
    }

    @Test
    fun unitLogMatchesChunkStyle() {
        assertEquals("[X Backup] Restoring MCA (0, 0)", RegionalRestore.formatUnitLog("MCA", 0, 0, true))
        assertEquals("[X Backup] Removing MCA (1, 2)", RegionalRestore.formatUnitLog("MCA", 1, 2, false))
        assertEquals("[X Backup] Restoring chunk (3, 4)", RegionalRestore.formatUnitLog("chunk", 3, 4, true))
        assertEquals("[X Backup] Removing chunk (5, 6)", RegionalRestore.formatUnitLog("chunk", 5, 6, false))
    }

    @Test
    fun planMcaLogsDeletesMissingBackupOnce() {
        val range = RegionalRestore.Range(0, 0, 2, 0)
        val plan = RegionalRestore.planMcaLogs(
            backupMcaPaths = listOf("region/r.0.0.mca"),
            worldMcaPaths = listOf(
                "region/r.1.0.mca",
                "entities/r.1.0.mca",
                "poi/r.1.0.mca",
            ),
            range = range,
        )
        assertEquals(listOf(0 to 0), plan.restore)
        assertEquals(listOf(1 to 0), plan.delete)
    }
}
