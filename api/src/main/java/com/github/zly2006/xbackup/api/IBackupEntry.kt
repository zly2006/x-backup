package com.github.zly2006.xbackup.api

import java.io.InputStream

interface IBackupEntry {
    val id: Int
    val path: String
    val size: Long
    val lastModified: Long
    val isDirectory: Boolean
    val hash: String
    val zippedSize: Long
    val compress: Int
    fun valid(service: XBackupApi): Boolean
    fun getInputStream(service: XBackupApi): InputStream?
}
