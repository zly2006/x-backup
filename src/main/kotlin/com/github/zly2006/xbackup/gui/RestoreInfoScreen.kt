package com.github.zly2006.xbackup.gui

import com.github.zly2006.xbackup.XBackup
import com.github.zly2006.xbackup.api.IBackup
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import java.nio.file.Path
import kotlin.io.path.name

class RestoreInfoScreen(private val backup: IBackup, private val worldRoot: Path) : Screen(Text.translatable("xb.gui.restore.title")) {
    private lateinit var reopenButton: ButtonWidget

    override fun init() {
        reopenButton = addDrawableChild(
            ButtonWidget.builder(Text.translatable("xb.gui.restore.reopen")) {
                reopenWorld()
            }.dimensions(width / 2 - 75, height - 52, 150, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("xb.gui.restore.close")) {
                client?.setScreen(null)
            }.dimensions(width / 2 - 75, height - 28, 150, 20).build()
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        //? if >= 1.20.4 {
        renderBackground(context, mouseX, mouseY, delta)
        //?} else {
        /*renderBackground(context)
        *///?}
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF)
        var y = height / 2 - 20
        val idText = Text.translatable("xb.gui.restore.id", backup.id)
        context.drawCenteredTextWithShadow(textRenderer, idText, width / 2, y, 0xFFFFFF)
        y += 12
        if (backup.comment.isNotEmpty()) {
            val comment = Text.translatable("xb.gui.restore.comment", backup.comment)
            context.drawCenteredTextWithShadow(textRenderer, comment, width / 2, y, 0xFFFFFF)
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
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("$progress%"), width / 2, yBar - 10, 0xFFFFFF)
        }
    }

    companion object {
        fun open(backup: IBackup, worldRoot: Path) {
            val client = MinecraftClient.getInstance()
            client.execute { client.setScreen(RestoreInfoScreen(backup, worldRoot)) }
        }
    }

    private fun reopenWorld() {
        val client = MinecraftClient.getInstance()
        client.setScreen(null)
        runCatching {
            val loader = client.createIntegratedServerLoader()
            //? if >= 1.20.4 {
            loader.start(worldRoot.normalize().name) {
                this.close()
            }
            //?} else {
            /*loader.start(this, worldRoot.normalize().name)
            *///?}
        }.onFailure { XBackup.log.error("Failed to reopen world", it) }
    }
}
