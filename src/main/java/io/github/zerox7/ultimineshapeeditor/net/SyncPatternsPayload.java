package io.github.zerox7.ultimineshapeeditor.net;

import io.github.zerox7.ultimineshapeeditor.UltimineShapeEditor;
import io.github.zerox7.ultimineshapeeditor.custom.CustomShapes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the player's full set of custom shapes (sent on login and after every save). */
public record SyncPatternsPayload(CustomShapes shapes) implements CustomPacketPayload {
    public static final Type<SyncPatternsPayload> TYPE = new Type<>(UltimineShapeEditor.id("sync_patterns"));

    public static final StreamCodec<ByteBuf, SyncPatternsPayload> STREAM_CODEC =
            CustomShapes.STREAM_CODEC.map(SyncPatternsPayload::new, SyncPatternsPayload::shapes);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
