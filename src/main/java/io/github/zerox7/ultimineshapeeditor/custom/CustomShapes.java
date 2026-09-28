package io.github.zerox7.ultimineshapeeditor.custom;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/** All of one player's custom shape slots. Always exactly {@link #SLOTS} entries. */
public record CustomShapes(List<ShapePattern> slots) {
    public static final int SLOTS = 10;

    public static final Codec<CustomShapes> CODEC =
            ShapePattern.CODEC.listOf().xmap(CustomShapes::new, CustomShapes::slots);

    public static final StreamCodec<ByteBuf, CustomShapes> STREAM_CODEC =
            ShapePattern.STREAM_CODEC.apply(ByteBufCodecs.list(SLOTS)).map(CustomShapes::new, CustomShapes::slots);

    public CustomShapes {
        List<ShapePattern> fixed = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            ShapePattern p = i < slots.size() ? slots.get(i) : null;
            fixed.add(p == null ? ShapePattern.EMPTY : p);
        }
        slots = List.copyOf(fixed);
    }

    public static CustomShapes empty() {
        return new CustomShapes(List.of());
    }

    public ShapePattern get(int slot) {
        return slot >= 0 && slot < SLOTS ? slots.get(slot) : ShapePattern.EMPTY;
    }

    public CustomShapes with(int slot, ShapePattern pattern) {
        List<ShapePattern> copy = new ArrayList<>(slots);
        copy.set(slot, pattern);
        return new CustomShapes(copy);
    }
}
