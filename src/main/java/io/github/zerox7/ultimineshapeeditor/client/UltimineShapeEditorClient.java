package io.github.zerox7.ultimineshapeeditor.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ftb.mods.ftbultimine.client.FTBUltimineClient;
import io.github.zerox7.ultimineshapeeditor.UltimineShapeEditor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only wiring: the editor key binding (K by default) and the /shapeeditor command. */
@Mod(value = UltimineShapeEditor.MOD_ID, dist = Dist.CLIENT)
public final class UltimineShapeEditorClient {
    /**
     * FTB Ultimine's own key binding category, taken from one of its key mappings. It must be the very same
     * object: the Controls screen starts a new heading whenever the category object changes, so an equal
     * copy would show a second "FTB Ultimine" heading.
     */
    private static final KeyMapping.Category ULTIMINE_CATEGORY = FTBUltimineClient.keyBindUltimine.getCategory();

    public static final KeyMapping OPEN_EDITOR = new KeyMapping("key.ultimineshapeeditor.editor",
            KeyConflictContext.IN_GAME, KeyModifier.NONE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, ULTIMINE_CATEGORY);

    // commands run while the chat screen is still open, so open the editor on the next tick instead
    private static boolean openNextTick = false;

    public UltimineShapeEditorClient(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(OPEN_EDITOR));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> clientTick());
        // "/shapeeditor" client command. If a future NeoForge 26.x drops RegisterClientCommandsEvent, delete this
        // listener (and its import): the K key still opens the editor.
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event ->
                event.getDispatcher().register(Commands.literal("shapeeditor").executes(ctx -> {
                    openNextTick = true;
                    return 1;
                })));
    }

    private static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new ShapeEditorScreen());
    }

    private static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (openNextTick) {
            openNextTick = false;
            openEditor();
            return;
        }
        while (OPEN_EDITOR.consumeClick()) {
            if (mc.screen == null) openEditor();
        }
    }
}