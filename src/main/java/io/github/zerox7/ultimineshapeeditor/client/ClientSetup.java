package io.github.zerox7.ultimineshapeeditor.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zerox7.ultimineshapeeditor.UltimineShapeEditor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client-only wiring: the editor keybind (K by default) and the /shapeeditor command. */
public final class ClientSetup {
    private ClientSetup() {}

    public static final KeyMapping OPEN_EDITOR = new KeyMapping("key.ultimineshapeeditor.editor",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.ftbultimine");

    // commands run while the chat screen is still open, so open the editor on the next tick instead
    private static boolean openNextTick = false;

    private static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new ShapeEditorScreen());
    }

    @EventBusSubscriber(modid = UltimineShapeEditor.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_EDITOR);
        }
    }

    @EventBusSubscriber(modid = UltimineShapeEditor.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
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

        @SubscribeEvent
        public static void registerCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("shapeeditor").executes(ctx -> {
                openNextTick = true;
                return 1;
            }));
        }
    }
}
