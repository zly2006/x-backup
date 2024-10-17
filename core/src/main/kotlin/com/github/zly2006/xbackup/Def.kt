package com.github.zly2006.xbackup

data class BackupConfig(
    val source: String,
    val target: String,
    val password: String,
    val compress: Boolean,
    val encrypt: Boolean,
    val progress: Boolean,
    val delete: Boolean
)

data class BackupResult(
    val success: Boolean,
    val message: String
)

data class BackupProgress(
    val total: Long,
    val current: Long,
    val message: String
)