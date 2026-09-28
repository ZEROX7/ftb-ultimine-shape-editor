package io.github.zerox7.ultimineshapeeditor.custom;

import io.github.zerox7.ultimineshapeeditor.UltimineShapeEditor;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, UltimineShapeEditor.MOD_ID);

    /** Each player's custom shapes; saved with the player and kept on death. */
    public static final Supplier<AttachmentType<CustomShapes>> CUSTOM_SHAPES = ATTACHMENTS.register("custom_shapes",
            () -> AttachmentType.builder(CustomShapes::empty)
                    .serialize(CustomShapes.CODEC)
                    .copyOnDeath()
                    .build());
}
