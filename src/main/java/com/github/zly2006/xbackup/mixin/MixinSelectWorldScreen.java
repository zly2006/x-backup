package com.github.zly2006.xbackup.mixin;

import com.github.zly2006.xbackup.BackupDatabaseService;
import com.github.zly2006.xbackup.XBackup;
//? if poly_lib {
import com.github.zly2006.xbackup.gui.BackupsGui;
//?}
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(SelectWorldScreen.class)
public class MixinSelectWorldScreen extends Screen {
    protected MixinSelectWorldScreen(Text title) {
        super(title);
    }

    //? if poly_lib {
    @Unique
    ButtonWidget buttonWidget;

    @Shadow
    private WorldListWidget levelList;

    @Inject(
            method = "init",
            at = @At("RETURN")
    )
    private void postInit(CallbackInfo ci) {
        buttonWidget = ButtonWidget.builder(Text.literal("回"),
                (button) -> {
                    if (!FabricLoader.getInstance().isModLoaded("polylib")) {
                        return;
                    }
                    if (levelList.getSelectedAsOptional().isPresent()) {
                        String name = levelList.getSelectedAsOptional().get().level.getName();
                        BackupDatabaseService service = new BackupDatabaseService(
                                Path.of("saves").toAbsolutePath().normalize(),
                                XBackup.INSTANCE.getDatabaseFromWorld(Path.of("saves", name)),
                                Path.of("").toAbsolutePath().resolve(XBackup.config.getBlobPath()).normalize(),
                                XBackup.config
                        );
                        BackupsGui.Companion.open(service, Path.of("saves", name));
                    }
                }).dimensions(this.width / 2 + 160, this.height - 28, 20, 20).build();
        buttonWidget.active = levelList.getSelectedAsOptional().isPresent();
        this.addDrawableChild(buttonWidget);
    }

    @Inject(
            method = "worldSelected",
            at = @At("RETURN")
    )
    private void worldSelected(CallbackInfo ci) {
        if (buttonWidget != null) {
            buttonWidget.active = levelList.getSelectedAsOptional().isPresent();
        }
    }

    @Inject(
            method = "render",
            at = @At("RETURN")
    )
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (buttonWidget != null && buttonWidget.isHovered()) {
            if (FabricLoader.getInstance().isModLoaded("polylib")) {
                setTooltip(Text.translatable("xb.button.backups"));
            } else {
                setTooltip(Text.translatable("xb.gui.no_polylib").formatted(Formatting.RED));
            }
        }
    }
    //?}
}
