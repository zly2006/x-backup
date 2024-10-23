package com.github.zly2006.xbackup.mc121_2

import com.github.zly2006.xbackup.Commands
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

object XB121_2: ModInitializer {
    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            Commands.register(dispatcher)
        }
    }
}
