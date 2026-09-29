package io.github.zerox7.ultimineshapeeditor.mixin;

import dev.ftb.mods.ftbultimine.FTBUltiminePlayerData;
import dev.ftb.mods.ftbultimine.shape.ShapeRegistry;
import io.github.zerox7.ultimineshapeeditor.custom.ShapeVisibility;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Server side: when a player cycles Ultimine shapes, skip custom slots they haven't defined,
 * so an empty slot can never be selected.
 */
@Mixin(value = FTBUltiminePlayerData.class, remap = false)
public abstract class FTBUltiminePlayerDataMixin {
    @Shadow @Final private UUID playerId;
    @Shadow private int shapeIndex;

    @Inject(method = "cycleShape", at = @At("TAIL"))
    private void ultimineshapeeditor$skipHiddenShapes(boolean next, CallbackInfo ci) {
        ServerPlayer player = ShapeVisibility.serverPlayer(playerId);
        if (player == null) return;
        shapeIndex = ShapeVisibility.firstVisible(shapeIndex, next, ShapeRegistry.INSTANCE.shapeCount(),
                ShapeVisibility.hiddenFor(player));
    }
}
