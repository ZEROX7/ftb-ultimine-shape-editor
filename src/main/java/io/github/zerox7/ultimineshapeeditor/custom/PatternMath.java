package io.github.zerox7.ultimineshapeeditor.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Turns a pattern's (right, up, depth) cells into world positions for a given mined face. */
public final class PatternMath {
    private PatternMath() {}

    /**
     * The three world directions a pattern is drawn in, matching what the player sees on screen:
     * "right" = right of the screen, "up" = top of the screen, "depth" = into the mined block.
     */
    public record Basis(Direction right, Direction up, Direction depth) {
        BlockPos apply(BlockPos origin, int r, int u, int d) {
            return origin.relative(right, r).relative(up, u).relative(depth, d);
        }
    }

    public static Basis basis(Direction face, Direction playerFacing) {
        Direction depth = face.getOpposite();
        if (face.getAxis().isHorizontal()) {
            // looking at a wall: screen-up is world up, screen-right is to the player's right
            return new Basis(depth.getClockWise(), Direction.UP, depth);
        }
        Direction forward = playerFacing.getAxis().isHorizontal() ? playerFacing : Direction.NORTH;
        // looking down at a floor the top of the screen points forward;
        // looking up at a ceiling it points backward. Right is the player's right either way.
        Direction up = face == Direction.UP ? forward : forward.getOpposite();
        return new Basis(forward.getClockWise(), up, depth);
    }

    private static final Comparator<Integer> NEAREST_FIRST = Comparator.<Integer>comparingInt(ShapePattern::depth)
            .thenComparingInt(p -> Math.abs(ShapePattern.right(p)) + Math.abs(ShapePattern.up(p)))
            .thenComparingInt(p -> p);

    /** Hard stop for repeats, in case a shape somehow never runs out of blocks (max blocks also limits it). */
    private static final int MAX_REPEATS = 4096;

    /**
     * The blocks to mine: the mined block first, then only cells that pass {@code check}, at most {@code max} in total.
     * <ol>
     *   <li>Every drawn layer once, layer by layer, nearest cells first.</li>
     *   <li>If the pattern repeats: the loop layers (earlier layers switched into the loop, then {@code repeatFrom}
     *       to the last drawn layer) again and again behind them, each repeat moved by the shift. Stops as soon as
     *       a whole repeat has nothing left to mine.</li>
     * </ol>
     * Nothing deeper than the pattern's max depth (if set) is ever included.
     */
    public static List<BlockPos> select(ShapePattern pattern, BlockPos origin, Direction face, Direction playerFacing,
                                        Predicate<BlockPos> check, int max) {
        Basis b = basis(face, playerFacing);
        List<BlockPos> out = new ArrayList<>();
        out.add(origin);
        if (max <= 1) return out;

        List<Integer> cells = new ArrayList<>(pattern.cells());
        cells.sort(NEAREST_FIRST);

        // 1. the drawn layers, once
        for (int p : cells) {
            if (!pattern.depthAllowed(ShapePattern.depth(p))) continue;
            BlockPos pos = b.apply(origin, ShapePattern.right(p), ShapePattern.up(p), ShapePattern.depth(p));
            if (check.test(pos)) {
                out.add(pos);
                if (out.size() >= max) return out;
            }
        }
        if (!pattern.repeats()) return out;

        // 2. the repeating section: the loop layers (included earlier layers + repeatFrom..last layer), in order.
        //    Repeat n puts them one after another behind the drawn layers, moved n times by the shift.
        List<Integer> loopLayers = pattern.loopLayerList();
        int length = loopLayers.size();
        int end = pattern.loopEnd();
        List<int[]> loop = new ArrayList<>(); // {right, up, position inside the loop}
        for (int j = 0; j < length; j++) {
            int layer = loopLayers.get(j);
            if (layer == 0) loop.add(new int[]{0, 0, j}); // the mined block is part of layer 1, so it repeats too
            for (int p : cells) {
                if (ShapePattern.depth(p) == layer) loop.add(new int[]{ShapePattern.right(p), ShapePattern.up(p), j});
            }
        }
        if (loop.isEmpty()) return out; // repeat set on empty layers: nothing to repeat

        for (int n = 1; n <= MAX_REPEATS; n++) {
            int start = end + 1 + (n - 1) * length; // depth of this repeat's first layer
            if (!pattern.depthAllowed(start)) break; // this whole repeat is past the max depth
            int before = out.size();
            for (int[] c : loop) {
                int depth = start + c[2];
                if (!pattern.depthAllowed(depth)) continue;
                BlockPos pos = b.apply(origin,
                        c[0] + n * pattern.shiftRight(),
                        c[1] + n * pattern.shiftUp(),
                        depth);
                if (check.test(pos)) {
                    out.add(pos);
                    if (out.size() >= max) return out;
                }
            }
            if (out.size() == before) break; // a whole repeat with nothing to mine: done
        }
        return out;
    }
}
