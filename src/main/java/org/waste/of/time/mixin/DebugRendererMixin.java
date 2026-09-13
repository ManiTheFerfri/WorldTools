package org.waste.of.time.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waste.of.time.Events;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    public void renderInject(
            Frustum frustum,
            double cameraX,
            double eyeHeight,
            double cameraZ,
            float tickDelta,
            CallbackInfo ci
    ) {
        Events.INSTANCE.onDebugRenderStart(cameraX, eyeHeight, cameraZ);
    }
}
