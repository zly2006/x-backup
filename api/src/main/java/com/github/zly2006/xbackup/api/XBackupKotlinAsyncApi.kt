package com.github.zly2006.xbackup.api

import org.jetbrains.exposed.sql.Transaction
import java.nio.file.Path

interface XBackupKotlinAsyncApi: XBackupApi {
    var activeTaskProgress: Int
    var activeTask: String

    suspend fun restore(id: Int, target: Path, ignored: (Path) -> Boolean)
    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T
    suspend fun <T> syncDbQuery(block: suspend Transaction.() -> T): T
}
