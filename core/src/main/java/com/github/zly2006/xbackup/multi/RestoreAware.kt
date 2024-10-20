package com.github.zly2006.xbackup.multi

interface RestoreAware {
    fun preRestore()

    fun postRestore()
}
