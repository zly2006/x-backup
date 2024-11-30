package com.github.zly2006.xbackup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Config {
    @Serializable
    class PruneConfig {
        @SerialName("enabled")
        var enabled = true

        @SerialName("keep_policy")
        val keepPolicy = mapOf(
            "1d" to "30m",
            "1w" to "6h",
            "1m" to "1d",
            "1y" to "1w",
            "2y" to "1m"
        )

        fun prune(idToTime: Map<String, Long>): List<String> {
            if (!enabled) return emptyList()
            var latest = Long.MAX_VALUE
            val ret = mutableListOf<String>()
            idToTime.forEach { (id, time) ->
                val diff = latest - time
                val policy = keepPolicy.entries.find { (k, _) -> diff < k.toMillis() }
                if (policy != null) {
                    if (diff < policy.value.toMillis()) {
                        ret.add(id)
                    } else {
                        latest = time
                    }
                }
            }
            return ret
        }

        private fun String.toMillis(): Long {
            val unit = substring(length - 1)
            val value = substring(0, length - 1).toLong()
            return when (unit) {
                "m" -> value * 60 * 1000
                "h" -> value * 60 * 60 * 1000
                "d" -> value * 24 * 60 * 60 * 1000
                "w" -> value * 7 * 24 * 60 * 60 * 1000
                "y" -> value * 365 * 24 * 60 * 60 * 1000
                else -> throw IllegalArgumentException("Invalid unit: $unit")
            }
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
