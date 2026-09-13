package org.waste.of.time.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.waste.of.time.Events;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(method = "removeEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;onRemoved()V", shift = At.Shift.AFTER))
    public void onEntityRemovedInject(final int entityId, final Entity.RemovalReason removalReason, final CallbackInfo ci,
                                      @Local Entity entity) {
        Events.INSTANCE.onEntityRemoved(entity, removalReason);
    }

    @Inject(method = "getMapState", at = @At("HEAD"))
    public void getMapStateInject(MapIdComponent id, CallbackInfoReturnable<MapState> cir) {
        Events.INSTANCE.onMapStateGet(id);
    }
}
