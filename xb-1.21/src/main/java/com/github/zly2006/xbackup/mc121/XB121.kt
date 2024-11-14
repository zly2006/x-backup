package com.github.zly2006.xbackup.mc121

import com.github.zly2006.xbackup.Commands
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

object XB121: ModInitializer {
    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            Commands.register(dispatcher)
        }
    }
}
