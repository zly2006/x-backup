package com.github.zly2006.xbackup.gui

//? if poly_lib {
import com.github.zly2006.xbackup.BackupDatabaseService
import kotlinx.coroutines.runBlocking
import net.creeperhost.polylib.client.modulargui.ModularGui
import net.creeperhost.polylib.client.modulargui.ModularGuiScreen
import net.creeperhost.polylib.client.modulargui.elements.*
import net.creeperhost.polylib.client.modulargui.lib.BackgroundRender
import net.creeperhost.polylib.client.modulargui.lib.Constraints
import net.creeperhost.polylib.client.modulargui.lib.GuiProvider
import net.creeperhost.polylib.client.modulargui.lib.GuiRender
import net.creeperhost.polylib.client.modulargui.lib.geometry.*
import net.creeperhost.polylib.client.modulargui.sprite.Material
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.world.SelectWorldScreen
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.text.SimpleDateFormat

class BackupsGui(private val parent: SelectWorldScreen, private val service: BackupDatabaseService) :
    GuiProvider {
    private var backupList: GuiList<BackupDatabaseService.Backup?>? = null
    private var selected: BackupDatabaseService.Backup? = null

    override fun createRootElement(gui: ModularGui?): GuiElement<*>? {
        return BMStyle.Flat.background(gui)
    }

    override fun buildGui(gui: ModularGui) {
        gui.renderScreenBackground(false)
        gui.initFullscreenGui()
        gui.guiTitle = Text.translatable("xb.gui.backups.title")

        val root = gui.root

        val title = GuiText(root!!, gui.guiTitle)
            .constrain(GeoParam.TOP, Constraint.relative(root[GeoParam.TOP], 5.0))
            .constrain(GeoParam.HEIGHT, Constraint.literal(8.0))
            .constrain(GeoParam.LEFT, Constraint.match(root[GeoParam.LEFT]))
            .constrain(GeoParam.RIGHT, Constraint.match(root[GeoParam.RIGHT]))

        val listBackground: GuiElement<*>? = GuiRectangle(root)
            .fill(-0x7fdfdfe0)
            .constrain(GeoParam.LEFT, Constraint.relative(root[GeoParam.LEFT], 10.0))
            .constrain(GeoParam.RIGHT, Constraint.relative(root[GeoParam.RIGHT], -10.0))
            .constrain(GeoParam.TOP, Constraint.relative(root[GeoParam.TOP], 20.0))
            .constrain(GeoParam.BOTTOM, Constraint.relative(root[GeoParam.BOTTOM], -24.0))

        val back = BMStyle.Flat.button(root, Text.translatable("xb.button.back_arrow"))
            .onPress { gui.mc().setScreen(gui.parentScreen) }
            .constrain(GeoParam.BOTTOM, Constraint.relative(listBackground!![GeoParam.TOP], -4.0))
            .constrain(GeoParam.LEFT, Constraint.match(listBackground[GeoParam.LEFT]))
            .constrain(GeoParam.WIDTH, Constraint.literal(50.0))
            .constrain(GeoParam.HEIGHT, Constraint.literal(12.0))

        val restore = BMStyle.Flat.buttonPrimary(root, Text.translatable("xb.button.restore_backup"))
            .setDisabled { selected == null }
            .onPress { restoreSelected(gui) }
            .constrain(GeoParam.TOP, Constraint.relative(listBackground[GeoParam.BOTTOM], 5.0))
            .constrain(GeoParam.LEFT, Constraint.match(listBackground[GeoParam.LEFT]))
            .constrain(GeoParam.WIDTH, Constraint.literal(150.0))
            .constrain(GeoParam.HEIGHT, Constraint.literal(14.0))

        val delete = BMStyle.Flat.buttonCaution(root, Text.translatable("xb.button.delete_backup"))
            .onPress { deleteSelected(gui) }
            .setDisabled { selected == null }
            .constrain(GeoParam.TOP, Constraint.relative(listBackground[GeoParam.BOTTOM], 5.0))
            .constrain(GeoParam.RIGHT, Constraint.match(listBackground[GeoParam.RIGHT]))
            .constrain(GeoParam.WIDTH, Constraint.literal(150.0))
            .constrain(GeoParam.HEIGHT, Constraint.literal(14.0))


        backupList = GuiList(
            listBackground
        )
        backupList!!.setDisplayBuilder { parent: GuiList<BackupDatabaseService.Backup?>?, backup: BackupDatabaseService.Backup? ->
            BackupElement(
                parent!!, backup!!
            )
        }
        Constraints.bind(backupList, listBackground, 2.0)

        val scrollBar = BMStyle.Flat.scrollBar(root, Axis.Y)
        scrollBar!!.container
            .setEnabled { backupList!!.hiddenSize() > 0 }
            .constrain(
                GeoParam.TOP, Constraint.match(
                    listBackground[GeoParam.TOP]
                )
            )
            .constrain(
                GeoParam.BOTTOM, Constraint.match(
                    listBackground[GeoParam.BOTTOM]
                )
            )
            .constrain(
                GeoParam.LEFT, Constraint.relative(
                    listBackground[GeoParam.RIGHT], 2.0
                )
            )
            .constrain(GeoParam.WIDTH, Constraint.literal(6.0))
        scrollBar.primary
            .setScrollableElement(backupList)
            .setSliderState(backupList!!.scrollState())

        updateList()
    }

    private fun deleteSelected(gui: ModularGui?) {
        if (selected == null) return
        runBlocking {
            service!!.deleteBackup(selected!!)
        }
        selected = null
        updateList()
    }

    private fun restoreSelected(gui: ModularGui) {
        if (selected == null) return
        runBlocking {
//            service!!.restore(selected!!.id, )
        }
        selected = null
        OptionDialog.simpleInfoDialog(
            gui,
            Text.translatable("xb.gui.backups.restored")
                .formatted(Formatting.GREEN)
        )
    }

    private fun updateList() {
        val backups = service!!.listBackups(0, Int.MAX_VALUE)
        backups.sortedWith(Comparator.comparingLong(BackupDatabaseService.Backup::created).reversed())
        backupList!!.list.clear()
        backupList!!.list.addAll(backups)
        backupList!!.rebuildElements()
    }

    inner class BackupElement(parent: GuiParent<*>, backup: BackupDatabaseService.Backup) :
        GuiElement<BackupElement?>(parent), BackgroundRender {
        private val backup: BackupDatabaseService.Backup? = backup

        init {
            this.constrain(GeoParam.HEIGHT, Constraint.literal(33.0))

            var leftOffset = 3

            val icon = backup.entries.firstOrNull { it.path == "icon.png" }
            val stream = runBlocking {
                icon?.getInputStream(service)
            }
            if (stream != null) {
                leftOffset = ySize().toInt() - 2
                val resourceLocation = Identifier.of("xbackup", "tmp/${backup.id}/icon.png")
                val texture = NativeImageBackedTexture(NativeImage.read(stream.readBytes()))
                texture.upload()
                mc().textureManager.registerTexture(resourceLocation, texture)

                GuiTexture(this) { Material.fromRawTexture(resourceLocation) }
                    .constrain(GeoParam.TOP, Constraint.relative(this[GeoParam.TOP], 1.0))
                    .constrain(GeoParam.LEFT, Constraint.relative(this[GeoParam.LEFT], 1.0))
                    .constrain(GeoParam.HEIGHT, Constraint.literal(leftOffset.toDouble()))
                    .constrain(GeoParam.WIDTH, Constraint.literal(leftOffset.toDouble()))
                leftOffset += 3
            }

            val name = GuiText(this, Text.literal("#" + backup.id).formatted(Formatting.AQUA))
                .setShadow(false)
                .setAlignment(Align.LEFT)
                .constrain(GeoParam.TOP, Constraint.relative(get(GeoParam.TOP), 3.0))
                .constrain(GeoParam.LEFT, Constraint.relative(get(GeoParam.LEFT), leftOffset.toDouble()))
                .constrain(GeoParam.RIGHT, Constraint.relative(get(GeoParam.RIGHT), -2.0))
                .constrain(GeoParam.HEIGHT, Constraint.literal(8.0))

            val created = GuiText(
                this,
                Text.literal(DATE_TIME_FORMAT.format(backup.created)).formatted(Formatting.GRAY)
            )
                .setShadow(false)
                .setAlignment(Align.LEFT)
                .constrain(GeoParam.TOP, Constraint.relative(name!![GeoParam.BOTTOM], 2.0))
                .constrain(GeoParam.LEFT, Constraint.relative(get(GeoParam.LEFT), leftOffset.toDouble()))
                .constrain(GeoParam.RIGHT, Constraint.relative(get(GeoParam.RIGHT), -2.0))
                .constrain(GeoParam.HEIGHT, Constraint.literal(8.0))

            val provText: Text = Text.literal("Backup")
            val provider = GuiText(this, provText)
                .setTooltip(Text.literal("Backup"))
                .setShadow(false)
                .setAlignment(Align.RIGHT)
                .constrain(GeoParam.TOP, Constraint.relative(get(GeoParam.TOP), 3.0))
                .constrain(GeoParam.WIDTH, Constraint.literal(font().getWidth(provText).toDouble()))
                .constrain(GeoParam.RIGHT, Constraint.relative(get(GeoParam.RIGHT), -2.0))
                .constrain(GeoParam.HEIGHT, Constraint.literal(8.0))

            val info = GuiTextList(this, listOf(Text.literal(backup.comment)))
                .setHorizontalAlign(Align.MIN)
                .constrain(GeoParam.LEFT, Constraint.relative(get(GeoParam.LEFT), leftOffset.toDouble()))
                .constrain(GeoParam.RIGHT, Constraint.relative(get(GeoParam.RIGHT), -2.0))
                .constrain(GeoParam.BOTTOM, Constraint.relative(get(GeoParam.BOTTOM), -2.0))
                .autoHeight()

            setTooltip(Text.literal(backup.comment))
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (isMouseOver) {
                selected = backup
            }

            return super.mouseClicked(mouseX, mouseY, button)
        }

        override fun renderBehind(render: GuiRender, mouseX: Double, mouseY: Double, partialTicks: Float) {
            val isSelected = selected == backup
            render.borderRect(
                rectangle,
                1.0,
                if (isMouseOver || isSelected) 0x10FFFFFF else 0,
                BMStyle.Flat.listEntryBorder(isSelected)
            )
        }
    }

    companion object {
        private val DATE_TIME_FORMAT = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss")
        fun open(parent: SelectWorldScreen, service: BackupDatabaseService) {
            MinecraftClient.getInstance().setScreen(ModularGuiScreen(BackupsGui(parent, service)))
        }
    }
}
//?} else {
/*class BackupsGui
*///?}
