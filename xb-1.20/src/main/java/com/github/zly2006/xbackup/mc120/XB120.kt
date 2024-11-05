package com.github.zly2006.xbackup.mc120

import com.github.zly2006.xbackup.Commands
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.jetbrains.exposed.sql.Database

object XB120: ModInitializer {
    override fun onInitialize() {
        Database.connect("jdbc:sqlite:file:worlds.db?mode=memory&cache=shared")
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            Commands.register(dispatcher)
        }
    }
}
