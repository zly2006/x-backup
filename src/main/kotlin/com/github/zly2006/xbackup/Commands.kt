package com.github.zly2006.xbackup

import com.mojang.brigadier.CommandDispatcher
import com.redenmc.bragadier.ktdsl.register
import net.minecraft.server.command.ServerCommandSource

object Commands {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register {
            literal("xb") {
                literal("create") {

                }
                literal("delete") {

                }
                literal("restore") {

                }
                literal("inspect") {

                }
                literal("list") {

                }
                literal("info") {

                }
                literal("export") {

                }
            }
        }
    }
}
