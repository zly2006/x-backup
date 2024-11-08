package com.github.zly2006.xbackup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Config {
    @SerialName("blob_path")
    val blobPath = "blob"

    @SerialName("backup_interval")
    var backupInterval = 3600
}
