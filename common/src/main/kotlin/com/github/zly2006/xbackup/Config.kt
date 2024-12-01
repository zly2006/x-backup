package com.github.zly2006.xbackup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Config {
    @Serializable
    class PruneConfig {
        @SerialName("enabled")
        var enabled = false

        @SerialName("keep_policy")
        val keepPolicy = mapOf(
            "1d" to "30m",
            "1w" to "6h",
            "1M" to "1d",
            "1y" to "1w",
            "2y" to "1M"
        )

        @SerialName("keep_temporary")
        val keepTemporary = "2d"

        fun temporaryKeepPolicy(): Long {
            return keepTemporary.toMillis()
        }

        fun prune(idToTime: Map<String, Long>, now: Long): List<String> {
            if (!enabled) return emptyList()
            var oldest = 0L
            val ret = mutableListOf<String>()
            val policies = keepPolicy.map { (k, v) -> k.toMillis() to v.toMillis() }.sortedBy { it.first }
            idToTime.toList().sortedBy { (_, time) -> time }.forEach { (id, time) ->
                val diff = time - oldest
                val policy = policies.lastOrNull { it.first <= now - time }
                if (policy != null) {
                    if (diff < policy.second) {
                        ret.add(id)
                    } else {
                        oldest = time
                    }
                }
            }
            return ret
        }

        private fun String.toMillis(): Long {
            val regex = Regex("(\\d+)([mhdwMy])")
            var startIndex = 0
            var ret = 0L
            while (startIndex < length) {
                val match = regex.find(this, startIndex) ?: break
                val (num, unit) = match.destructured
                val value = num.toLong()
                startIndex = match.range.last + 1
                ret += when (unit) {
                    "m" -> value * 60 * 1000
                    "h" -> value * 60 * 60 * 1000
                    "d" -> value * 24 * 60 * 60 * 1000
                    "w" -> value * 7 * 24 * 60 * 60 * 1000
                    "M" -> value * 30 * 24 * 60 * 60 * 1000
                    "y" -> value * 365 * 24 * 60 * 60 * 1000
                    else -> throw IllegalArgumentException("Unknown unit: $unit")
                }
            }
            return ret
        }
    }

    @SerialName("ignored_files")
    val ignoredFiles: List<String> = listOf(
        "session.lock",
        "fake_player.gca.json",
        "ledger.sqlite"
    )

    @SerialName("blob_path")
    val blobPath = "blob"

    @SerialName("backup_interval")
    var backupInterval = 10800

    @SerialName("mirror_mode")
    var mirrorMode: Boolean = false

    @SerialName("mirror_from")
    var mirrorFrom: String? = null

    @SerialName("language")
    var language = "en_us"

    @SerialName("prune")
    val pruneConfig = PruneConfig()
}
