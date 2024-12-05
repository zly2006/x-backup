package com.github.zly2006.xbackup

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("X Backup")!!

suspend inline fun <T> retry(times: Int, function: () -> T): T {
    var lastException: Throwable? = null
    repeat(times) {
        try {
            return function()
        } catch (e: Throwable) {
            log.error("Error in retry, attempt ${it + 1}/$times", e)
            lastException = e
            delay(1000)
        }
    }
    throw RuntimeException("Retry failed", lastException)
}
