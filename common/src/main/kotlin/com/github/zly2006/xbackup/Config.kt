package com.github.zly2006.xbackup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Config {
    @SerialName("ignored_files")
    val ignoredFiles: List<String> = listOf(
        "session.lock",
        "fake_player.gca.json",
        "ledger.sqlite"
    )

    @SerialName("blob_path")
    val blobPath = "blob"

    @SerialName("backup_interval")
    var backupInterval = 3600

    @SerialName("mirror_mode")
    var mirrorMode: Boolean = false

    @SerialName("mirror_from")
    var mirrorFrom: String? = null

    @SerialName("language")
    var language = "en_us"
}
