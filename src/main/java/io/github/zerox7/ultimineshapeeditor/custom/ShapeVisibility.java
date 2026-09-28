package io.github.zerox7.ultimineshapeeditor.custom;

import dev.ftb.mods.ftbultimine.api.shape.Shape;
import dev.ftb.mods.ftbultimine.shape.ShapeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Which of Ultimine's shapes a player can see: every built-in shape, plus only the custom slots they have defined.
 * Used by the mixins on both sides. No client-only imports, so it is safe everywhere.
 */
public final class ShapeVisibility {
    private ShapeVisibility() {}

    /** Is the shape at this index one of our custom slots that is empty in {@code shapes}? */
    public static boolean isHidden(CustomShapes shapes, int index) {
        Shape shape = ShapeRegistry.INSTANCE.getShape(index);
        return shape instanceof CustomSlotShape custom && !shapes.get(custom.slot()).isDefined();
    }

    public static IntPredicate hiddenFor(CustomShapes shapes) {
        return i -> isHidden(shapes, i);
    }

    /** Server side: the hidden-slot test for one player. */
    public static IntPredicate hiddenFor(ServerPlayer player) {
        return hiddenFor(player.getData(ModAttachments.CUSTOM_SHAPES));
    }

    /** Client side: the hidden-slot test for the local player (from the synced cache). */
    public static IntPredicate hiddenForClient() {
        return hiddenFor(ClientPatternCache.get());
    }

    public static int visibleCount(int total, IntPredicate hidden) {
        int n = 0;
        for (int i = 0; i < total; i++) if (!hidden.test(i)) n++;
        return Math.max(n, 1);
    }

    /**
     * The index {@code offset} visible shapes away from {@code current} (negative = backwards), wrapping around
     * and skipping hidden ones. Offset 0 is {@code current} itself.
     */
    public static int visibleNeighbour(int current, int offset, int total, IntPredicate hidden) {
        if (offset == 0 || total <= 0) return current;
        int step = offset > 0 ? 1 : -1;
        int want = Math.abs(offset);
        int idx = current;
        for (int tries = 0; tries < total * want; tries++) {
            idx = Math.floorMod(idx + step, total);
            if (!hidden.test(idx) && --want == 0) return idx;
        }
        return Math.floorMod(current + offset, total); // nothing visible: behave like Ultimine does
    }

    /** From {@code index}, the next visible shape in the given direction (or {@code index} itself if visible). */
    public static int firstVisible(int index, boolean forward, int total, IntPredicate hidden) {
        int idx = Math.floorMod(index, total);
        for (int tries = 0; tries < total && hidden.test(idx); tries++) {
            idx = Math.floorMod(idx + (forward ? 1 : -1), total);
        }
        return idx;
    }

    @Nullable
    public static ServerPlayer serverPlayer(UUID id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }
}
