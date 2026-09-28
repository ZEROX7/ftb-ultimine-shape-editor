package io.github.zerox7.ultimineshapeeditor.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A player-drawn shape: a name, a set of cells relative to the mined block, and optional repeat settings.
 * <p>
 * Each cell is (right, up, depth) as seen when looking at the face you mine:
 * right/up are across the face (-RADIUS..RADIUS), depth goes into the block (0..MAX_DEPTH-1).
 * Cells are packed into one int each. The mined block itself (0,0,0) is always mined and never stored.
 * <p>
 * Repeating: if {@code repeatFrom} is a layer index (not {@link #NO_REPEAT}), the layers from there down to the
 * last drawn layer form a loop that is repeated deeper and deeper into the block. Each repeat is moved by
 * ({@code shiftRight}, {@code shiftUp}) compared to the one before, and repeating stops when a whole repeat
 * has nothing left to mine (the same idea as FTB Ultimine's own tunnels).
 * <p>
 * {@code maxDepth}: if not {@link #NO_LIMIT}, nothing deeper than that many layers is mined, repeating or not.
 */
public record ShapePattern(String name, List<Integer> cells, int repeatFrom, int shiftRight, int shiftUp, int maxDepth) {
    public static final int RADIUS = 7;                       // grid is SIZE x SIZE
    public static final int SIZE = RADIUS * 2 + 1;            // 15
    public static final int MAX_DEPTH = 15;                   // layers 0..14
    public static final int MAX_CELLS = SIZE * SIZE * MAX_DEPTH;
    public static final int MAX_NAME = 32;
    public static final int MAX_SHIFT = RADIUS;
    public static final int NO_REPEAT = -1;
    public static final int NO_LIMIT = 0;
    public static final int MAX_DEPTH_LIMIT = 999;
    public static final int ORIGIN = pack(0, 0, 0);
    public static final ShapePattern EMPTY = new ShapePattern("", List.of(), NO_REPEAT, 0, 0, NO_LIMIT);

    private static final String CODE_V1 = "UXS1:"; // [nameLen][name][bits]
    private static final String CODE_V2 = "UXS2:"; // [flags: 1 = repeat layer 1][nameLen][name][bits]
    private static final String CODE_V3 = "UXS3:"; // [repeatFrom+1][shiftRight+64][shiftUp+64][nameLen][name][bits]
    private static final String CODE_V4 = "UXS4:"; // as v3 with [maxDepth hi][maxDepth lo] after shiftUp

    public static final Codec<ShapePattern> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(ShapePattern::name),
            Codec.INT.listOf().optionalFieldOf("cells", List.of()).forGetter(ShapePattern::cells),
            Codec.INT.optionalFieldOf("repeat_from", NO_REPEAT).forGetter(ShapePattern::repeatFrom),
            Codec.INT.optionalFieldOf("shift_right", 0).forGetter(ShapePattern::shiftRight),
            Codec.INT.optionalFieldOf("shift_up", 0).forGetter(ShapePattern::shiftUp),
            Codec.INT.optionalFieldOf("max_depth", NO_LIMIT).forGetter(ShapePattern::maxDepth)
    ).apply(i, ShapePattern::sanitized));

    public static final StreamCodec<ByteBuf, ShapePattern> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME * 4), ShapePattern::name,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_CELLS)), ShapePattern::cells,
            ByteBufCodecs.VAR_INT, ShapePattern::repeatFrom,
            ByteBufCodecs.VAR_INT, ShapePattern::shiftRight,
            ByteBufCodecs.VAR_INT, ShapePattern::shiftUp,
            ByteBufCodecs.VAR_INT, ShapePattern::maxDepth,
            ShapePattern::sanitized
    );

    public ShapePattern {
        name = name == null ? "" : name;
        cells = List.copyOf(cells);
    }

    /** Builds a pattern from untrusted input: trims the name, drops invalid/duplicate cells, clamps the settings. */
    public static ShapePattern sanitized(String name, Collection<Integer> cells, int repeatFrom, int shiftRight, int shiftUp,
                                         int maxDepth) {
        String n = name == null ? "" : name.replaceAll("[\\p{Cntrl}§]", "").strip();
        if (n.length() > MAX_NAME) n = n.substring(0, MAX_NAME);
        List<Integer> c = cells.stream()
                .filter(p -> p != null && isValid(p) && p != ORIGIN)
                .distinct().sorted().toList();
        int from = repeatFrom < 0 || repeatFrom >= MAX_DEPTH ? NO_REPEAT : repeatFrom;
        int sr = from == NO_REPEAT ? 0 : clampShift(shiftRight);
        int su = from == NO_REPEAT ? 0 : clampShift(shiftUp);
        int md = maxDepth <= 0 ? NO_LIMIT : Math.min(maxDepth, MAX_DEPTH_LIMIT);
        return new ShapePattern(n, c, from, sr, su, md);
    }

    public boolean hasDepthLimit() {
        return maxDepth != NO_LIMIT;
    }

    /** Whether a block {@code depth} layers into the face (0 = the mined face) may be mined. */
    public boolean depthAllowed(int depth) {
        return maxDepth == NO_LIMIT || depth < maxDepth;
    }

    public static int clampShift(int s) {
        return Math.max(-MAX_SHIFT, Math.min(MAX_SHIFT, s));
    }

    /**
     * Whether the player has actually made a shape here: at least one drawn cell, or repeating turned on
     * (repeating the mined block alone is a valid 1-wide tunnel). Undefined slots are hidden in Ultimine's menu.
     */
    public boolean isDefined() {
        return !cells.isEmpty() || repeats();
    }

        public boolean repeats() {
        return repeatFrom != NO_REPEAT;
    }

    /** The deepest layer with any drawn cell (0 if only the mined block). */
    public int lastLayer() {
        return lastLayer(cells);
    }

    public static int lastLayer(Collection<Integer> cells) {
        int max = 0;
        for (int p : cells) max = Math.max(max, depth(p));
        return max;
    }

    /** Last layer of the repeating section: from repeatFrom to the deepest drawn layer (at least repeatFrom). */
    public int loopEnd() {
        return Math.max(repeatFrom, lastLayer());
    }

    // ---- cell packing ----

    public static int pack(int right, int up, int depth) {
        return (depth * SIZE + (up + RADIUS)) * SIZE + (right + RADIUS);
    }

    public static boolean inBounds(int right, int up, int depth) {
        return Math.abs(right) <= RADIUS && Math.abs(up) <= RADIUS && depth >= 0 && depth < MAX_DEPTH;
    }

    public static boolean isValid(int packed) {
        return packed >= 0 && packed < MAX_CELLS;
    }

    public static int right(int packed) {
        return packed % SIZE - RADIUS;
    }

    public static int up(int packed) {
        return (packed / SIZE) % SIZE - RADIUS;
    }

    public static int depth(int packed) {
        return packed / (SIZE * SIZE);
    }

    // ---- share codes ----

    /** A copy-pasteable text code for sharing a pattern. Even a full pattern stays around 600 characters. */
    public String toShareCode() {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8); // <= MAX_NAME * 4 = 128 bytes
        BitSet bits = new BitSet(MAX_CELLS);
        for (int c : cells) bits.set(c);
        byte[] cellBytes = bits.toByteArray();
        byte[] out = new byte[6 + nameBytes.length + cellBytes.length];
        out[0] = (byte) (repeatFrom + 1);
        out[1] = (byte) (shiftRight + 64);
        out[2] = (byte) (shiftUp + 64);
        out[3] = (byte) (maxDepth >> 8);
        out[4] = (byte) maxDepth;
        out[5] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, out, 6, nameBytes.length);
        System.arraycopy(cellBytes, 0, out, 6 + nameBytes.length, cellBytes.length);
        return CODE_V4 + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
    }

    /** Reads a share code; older codes (UXS1-UXS3) are still accepted. */
    public static Optional<ShapePattern> fromShareCode(String code) {
        if (code == null) return Optional.empty();
        code = code.strip();
        int version;
        if (code.startsWith(CODE_V4)) version = 4;
        else if (code.startsWith(CODE_V3)) version = 3;
        else if (code.startsWith(CODE_V2)) version = 2;
        else if (code.startsWith(CODE_V1)) version = 1;
        else return Optional.empty();

        byte[] data;
        try {
            data = Base64.getUrlDecoder().decode(code.substring(CODE_V1.length())); // all prefixes are 5 chars
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        int header = switch (version) { case 4 -> 5; case 3 -> 3; case 2 -> 1; default -> 0; };
        if (data.length < header + 1) return Optional.empty();
        int repeatFrom = NO_REPEAT, shiftRight = 0, shiftUp = 0, maxDepth = NO_LIMIT;
        if (version >= 3) {
            repeatFrom = (data[0] & 0xFF) - 1;
            shiftRight = (data[1] & 0xFF) - 64;
            shiftUp = (data[2] & 0xFF) - 64;
            if (version == 4) maxDepth = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
        } else if (version == 2 && (data[0] & 1) != 0) {
            repeatFrom = 0; // v2 "infinite" meant: repeat layer 1
        }

        int pos = header;
        int nameLen = data[pos++] & 0xFF;
        if (pos + nameLen > data.length) return Optional.empty();
        String n = new String(data, pos, nameLen, StandardCharsets.UTF_8);
        BitSet bits = BitSet.valueOf(Arrays.copyOfRange(data, pos + nameLen, data.length));
        List<Integer> cells = new ArrayList<>();
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) cells.add(i);
        if (version == 2 && repeatFrom == 0) cells.removeIf(p -> depth(p) > 0); // v2 ignored deeper layers
        return Optional.of(sanitized(n, cells, repeatFrom, shiftRight, shiftUp, maxDepth)); // clamps everything
    }
}
