package com.github.zly2006.xbackup.gui

import com.github.zly2006.xbackup.XBackup
import com.github.zly2006.xbackup.api.IBackup
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import java.nio.file.Path


class RestoreInfoScreen(private val backup: IBackup, private val worldRoot: Path) : Screen(Component.translatable("xb.gui.restore.title")) {
    private lateinit var reopenButton: Button

    override fun init() {
        reopenButton = addRenderableWidget(
            Button.builder(Component.translatable("xb.gui.restore.reopen")) {
                reopenWorld()
            }.bounds(width / 2 - 75, height - 52, 150, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("xb.gui.restore.close")) {
                minecraft?.setScreen(null)
            }.bounds(width / 2 - 75, height - 28, 150, 20).build()
        )
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractBackground(context, mouseX, mouseY, delta)
        super.extractRenderState(context, mouseX, mouseY, delta)
        context.centeredText(font, title, width / 2, 20, 0xFFFFFF)
        var y = height / 2 - 20
        val idText = Component.translatable("xb.gui.restore.id", backup.id)
        context.centeredText(font, idText, width / 2, y, 0xFFFFFF)
        y += 12
        if (backup.comment.isNotEmpty()) {
            val comment = Component.translatable("xb.gui.restore.comment", backup.comment)
            context.centeredText(font, comment, width / 2, y, 0xFFFFFF)
        }

        val progress = XBackup.service.activeTaskProgress
        if (progress in 0..100) {
            val barWidth = 150
            val barHeight = 8
            val x = (width - barWidth) / 2
            val yBar = height / 2 + 20
            context.fill(x, yBar, x + barWidth, yBar + barHeight, 0xFF555555.toInt())
            val w = (barWidth * progress) / 100
            if (w > 0) {
                context.fill(x + 1, yBar + 1, x + w - 1, yBar + barHeight - 1, 0xFF00FF00.toInt())
            }
            context.centeredText(font, Component.literal("$progress%"), width / 2, yBar - 10, 0xFFFFFF)
        }
    }

    companion object {
        fun open(backup: IBackup, worldRoot: Path) {
            val client = Minecraft.getInstance()
            client.execute { client.setScreen(RestoreInfoScreen(backup, worldRoot)) }
        }
    }

    private fun reopenWorld() {
        val client = Minecraft.getInstance()
        client.setScreen(null)
        runCatching {
            val loader = client.createWorldOpenFlows()
            loader.openWorld(worldRoot.normalize().fileName.toString()) {
                // Callback (can be empty)
            }
        }.onFailure { XBackup.log.error("Failed to reopen world", it) }
    }
}
