package com.github.zly2006.xbackup

import com.github.zly2006.xbackup.access.ServerAccess
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

                }.executes {
                    val access = it.source.server as ServerAccess
                    access.restoreStart()
                    1
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
