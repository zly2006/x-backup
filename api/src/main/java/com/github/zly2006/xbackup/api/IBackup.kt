package com.github.zly2006.xbackup.api

interface IBackup {
    val id: Int
    val size: Long
    val zippedSize: Long
    val created: Long
    val comment: String
    val entries: List<IBackupEntry>
    val temporary: Boolean
    val cloudBackupUrl: String?
}
