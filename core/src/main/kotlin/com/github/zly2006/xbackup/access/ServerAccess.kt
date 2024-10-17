package com.github.zly2006.xbackup.access

interface ServerAccess {
    /**
     * Blocks all incoming connections, drop all loaded chunks.
     */
    fun restoreStart()

    fun restoreStop()

    fun saveWorld()
}
