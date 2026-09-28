package io.github.zerox7.ultimineshapeeditor.custom;

import dev.ftb.mods.ftbultimine.api.shape.Shape;
import dev.ftb.mods.ftbultimine.api.shape.ShapeContext;
import io.github.zerox7.ultimineshapeeditor.UltimineShapeEditor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * One of the fixed "Custom Shape N" entries in Ultimine's shape list. What it mines is looked up
 * from the mining player's own saved pattern for this slot, so every player can draw their own.
 */
public class CustomSlotShape implements Shape {
    private final int slot;
    private final Identifier id;

    public CustomSlotShape(int slot) {
        this.slot = slot;
        this.id = UltimineShapeEditor.id("custom_" + (slot + 1));
    }

    public int slot() {
        return slot;
    }

    @Override
    public Identifier getName() {
        return id;
    }

    @Override
    public List<BlockPos> getBlocks(ShapeContext context) {
        ShapePattern pattern = context.player().getData(ModAttachments.CUSTOM_SHAPES).get(slot);
        // the mined block is always first, as in Ultimine's own shapes; the rest must pass Ultimine's block check
        return PatternMath.select(pattern, context.origPos(), context.face(), context.player().getDirection(),
                context::check, context.maxBlocks());
    }

    /**
     * Ultimine only calls this on the client (shape menu and hotbar message), so the player's own
     * name for the slot is shown there. Falls back to "Custom Shape N".
     */
    @Override
    @SuppressWarnings("UnstableApiUsage")
    public MutableComponent getDisplayName() {
        String name = ClientPatternCache.get().get(slot).name();
        return name.isBlank()
                ? Component.translatable("ftbultimine.shape." + id.toLanguageKey())
                : Component.literal(name);
    }
}
