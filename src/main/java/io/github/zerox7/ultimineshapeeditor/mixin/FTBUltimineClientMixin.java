package io.github.zerox7.ultimineshapeeditor.mixin;

import dev.ftb.mods.ftbultimine.api.shape.Shape;
import dev.ftb.mods.ftbultimine.client.FTBUltimineClient;
import dev.ftb.mods.ftbultimine.shape.ShapeRegistry;
import io.github.zerox7.ultimineshapeeditor.custom.ShapeVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Client side: Ultimine's shape menu (hold the Ultimine key + sneak) lists the shapes around the current one.
 * Make it list only visible shapes, i.e. skip custom slots the player hasn't defined.
 */
@Mixin(value = FTBUltimineClient.class, remap = false)
public abstract class FTBUltimineClientMixin {
    @Shadow private int shapeIdx;

    /** The menu asks for getShape(shapeIdx + i); answer with the i-th visible neighbour instead. */
    @Redirect(method = "addPressedInfo", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbultimine/shape/ShapeRegistry;getShape(I)Ldev/ftb/mods/ftbultimine/api/shape/Shape;"))
    private Shape ultimineshapeeditor$visibleNeighbour(ShapeRegistry registry, int index) {
        int idx = ShapeVisibility.visibleNeighbour(shapeIdx, index - shapeIdx, registry.shapeCount(),
                ShapeVisibility.hiddenForClient());
        return registry.getShape(idx);
    }

    /** Menu size and scrollbar are based on the shape count; use the number of visible shapes. */
    @Redirect(method = {"addPressedInfo", "renderGameOverlay"}, at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbultimine/shape/ShapeRegistry;shapeCount()I"))
    private int ultimineshapeeditor$visibleCount(ShapeRegistry registry) {
        return ShapeVisibility.visibleCount(registry.shapeCount(), ShapeVisibility.hiddenForClient());
    }
}
