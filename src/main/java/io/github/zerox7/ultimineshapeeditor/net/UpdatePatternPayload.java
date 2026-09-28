package io.github.zerox7.ultimineshapeeditor.net;

import io.github.zerox7.ultimineshapeeditor.UltimineShapeEditor;
import io.github.zerox7.ultimineshapeeditor.custom.ShapePattern;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "save this pattern into slot N". */
public record UpdatePatternPayload(int slot, ShapePattern pattern) implements CustomPacketPayload {
    public static final Type<UpdatePatternPayload> TYPE = new Type<>(UltimineShapeEditor.id("update_pattern"));

    public static final StreamCodec<ByteBuf, UpdatePatternPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpdatePatternPayload::slot,
            ShapePattern.STREAM_CODEC, UpdatePatternPayload::pattern,
            UpdatePatternPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
