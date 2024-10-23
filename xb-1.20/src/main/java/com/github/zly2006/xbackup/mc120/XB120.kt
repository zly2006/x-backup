package com.github.zly2006.xbackup.mc120

import com.github.zly2006.xbackup.Commands
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

object XB120: ModInitializer {
    @JvmField
    var isBlockingChunkLoading = false
    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            Commands.register(dispatcher)
        }
    }
}
