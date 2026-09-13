package org.waste.of.time.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waste.of.time.Events;
import org.waste.of.time.gui.ManagerScreen;
import org.waste.of.time.manager.CaptureManager;
import org.waste.of.time.manager.MessageManager;

@Mixin(PauseScreen.class)
public class GameMenuScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    public void onInit(final CallbackInfo ci) {
        PauseScreen self = (PauseScreen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        Component label = CaptureManager.INSTANCE.getCapturing()
                ? MessageManager.INSTANCE.translateHighlight("worldtools.gui.escape.button.finish_download", CaptureManager.INSTANCE.getCurrentLevelName())
                : MessageManager.INSTANCE.getBrand();
        Button button = Button.builder(label, b -> {
            if (CaptureManager.INSTANCE.getCapturing()) {
                CaptureManager.INSTANCE.stop();
                client.gui.setScreen(null);
            } else {
                client.gui.setScreen(ManagerScreen.INSTANCE);
            }
        }).width(204).build();
        button.setX(10);
        button.setY(self.height - 30);
        ((ScreenAccessor) (Object) self).wt$addRenderableWidget(button);
    }
}
