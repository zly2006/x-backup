package com.github.zly2006.xbackup

import net.minecraft.text.Text

interface Task {
    val name: String
    val displayName: Text
    val startedAt: Long
    val finishedAt: Long
    val status: Status
    val failure: Throwable?

    enum class Status {
        WAITING,
        RUNNING,
        FINISHED,
        FAILED
    }

    suspend fun execute()
}
