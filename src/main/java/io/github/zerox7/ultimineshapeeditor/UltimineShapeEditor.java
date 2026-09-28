package io.github.zerox7.ultimineshapeeditor;

import dev.ftb.mods.ftbultimine.api.neoforge.FTBUltimineEvent;
import dev.ftb.mods.ftbultimine.api.shape.RegisterShapeEvent;
import io.github.zerox7.ultimineshapeeditor.custom.CustomShapes;
import io.github.zerox7.ultimineshapeeditor.custom.CustomSlotShape;
import io.github.zerox7.ultimineshapeeditor.custom.ModAttachments;
import io.github.zerox7.ultimineshapeeditor.net.ModNetwork;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * FTB Ultimine Shape Editor: players draw their own Ultimine shapes in an in-game editor.
 * <p>
 * Ultimine's list of shapes is fixed once it is built, so {@link CustomShapes#SLOTS} "custom slot" shapes are
 * registered; what each one mines is looked up per player. Slots a player hasn't defined are hidden from their
 * shape menu and skipped when cycling (see the mixins).
 * <p>
 * Client-only setup (key binding, command, editor screen) lives in {@code UltimineShapeEditorClient}.
 */
@Mod(UltimineShapeEditor.MOD_ID)
public class UltimineShapeEditor {
    public static final String MOD_ID = "ultimineshapeeditor";

    public UltimineShapeEditor(IEventBus modBus) {
        ModAttachments.ATTACHMENTS.register(modBus);
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, ModNetwork::onLogin);

        // FTB Ultimine posts this on the NeoForge game bus whenever it builds its shape list
        // (once for the client, once each time a server starts).
        NeoForge.EVENT_BUS.addListener(FTBUltimineEvent.RegisterShape.class,
                event -> registerShapes(event.getEventData()));
    }

    private static void registerShapes(RegisterShapeEvent.Data registry) {
        for (int slot = 0; slot < CustomShapes.SLOTS; slot++) {
            registry.register(new CustomSlotShape(slot));
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
