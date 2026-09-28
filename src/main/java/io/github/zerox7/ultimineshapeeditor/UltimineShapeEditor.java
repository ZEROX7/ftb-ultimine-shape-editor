package io.github.zerox7.ultimineshapeeditor;

import dev.ftb.mods.ftbultimine.api.shape.RegisterShapeEvent;
import io.github.zerox7.ultimineshapeeditor.custom.CustomShapes;
import io.github.zerox7.ultimineshapeeditor.custom.CustomSlotShape;
import io.github.zerox7.ultimineshapeeditor.custom.ModAttachments;
import io.github.zerox7.ultimineshapeeditor.net.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * FTB Ultimine Shape Editor: players draw their own Ultimine shapes in an in-game editor.
 * <p>
 * Ultimine's shape list is fixed at startup, so {@link CustomShapes#SLOTS} "custom slot" shapes are registered
 * once; what each one mines is looked up per player. Slots a player hasn't defined are hidden from their
 * shape menu and skipped when cycling (see the mixins).
 */
@Mod(UltimineShapeEditor.MOD_ID)
public class UltimineShapeEditor {
    public static final String MOD_ID = "ultimineshapeeditor";

    public UltimineShapeEditor(IEventBus modBus) {
        ModAttachments.ATTACHMENTS.register(modBus);
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.addListener(ModNetwork::onLogin);

        // FTB Ultimine fires this event during common setup (after all mod constructors),
        // so registering the listener here is early enough.
        RegisterShapeEvent.REGISTER.register(UltimineShapeEditor::registerShapes);
    }

    private static void registerShapes(RegisterShapeEvent.Registry registry) {
        for (int slot = 0; slot < CustomShapes.SLOTS; slot++) {
            registry.register(new CustomSlotShape(slot));
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
