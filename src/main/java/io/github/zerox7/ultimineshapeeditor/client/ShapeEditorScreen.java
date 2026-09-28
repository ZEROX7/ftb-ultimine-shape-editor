package io.github.zerox7.ultimineshapeeditor.client;

import io.github.zerox7.ultimineshapeeditor.custom.ClientPatternCache;
import io.github.zerox7.ultimineshapeeditor.custom.CustomShapes;
import io.github.zerox7.ultimineshapeeditor.custom.ShapePattern;
import io.github.zerox7.ultimineshapeeditor.net.UpdatePatternPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.client.input.MouseButtonEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Grid editor for the custom shape slots. The grid is one layer of the shape as seen when looking at
 * the face you mine (top of grid = top of your screen); layer 1 is the face itself, later layers go
 * deeper into the wall/floor/ceiling. From a chosen layer on, the shape can repeat forever, shifted a
 * little each time (see {@link ShapePattern}).
 */
public class ShapeEditorScreen extends Screen {
    private static final int R = ShapePattern.RADIUS;
    private static final int SIZE = ShapePattern.SIZE;
    private static final int LAYERS = ShapePattern.MAX_DEPTH;
    private static final int SLOTS = CustomShapes.SLOTS;
    private static final int PANEL = 96;

    private static final int COL_CELL_A = 0xFF2E2E2E;
    private static final int COL_CELL_B = 0xFF363636;
    private static final int COL_AXIS = 0xFF444444;
    private static final int COL_SET = 0xFF4FA3E0;
    private static final int COL_GHOST = 0x884FA3E0;
    private static final int COL_ORIGIN = 0xFFE0B040;
    private static final int COL_ORIGIN_MARK = 0xFF7A6020;
    private static final int COL_LOOP = 0xFF3FC9C9;      // frame around layers that repeat
    private static final int COL_NEXT = 0xFFFF9A3C;      // where the next repeat of this layer lands

    private static int lastSlot = 0;

    // working copy of all slots, so switching slots doesn't lose edits
    private final String[] names = new String[SLOTS];
    private final List<Set<Integer>> cells = new ArrayList<>(SLOTS);
    private final boolean[] dirty = new boolean[SLOTS];
    private final int[] repeatFrom = new int[SLOTS];
    private final int[] shiftRight = new int[SLOTS];
    private final int[] shiftUp = new int[SLOTS];
    private final int[] maxDepth = new int[SLOTS];
    private final int[] loopMask = new int[SLOTS];   // earlier layers switched into the loop (bit n = layer n+1)

    private int slot = lastSlot;
    private int layer = 0;
    private int cell, gridX, gridY, gridW, panelX;
    private EditBox nameBox;
    private Button prevLayerBtn, nextLayerBtn, repeatBtn, inLoopBtn, copyPrevBtn;
    private final List<Button> shiftButtons = new ArrayList<>();
    private Button depthMinusBtn, depthPlusBtn;
    private final List<Button> slotTabs = new ArrayList<>();
    @Nullable private Boolean paintValue;     // what a drag paints: true = add, false = remove
    @Nullable private Component status;
    private long statusUntil;
    private boolean confirmDiscard;

    public ShapeEditorScreen() {
        super(Component.translatable("ultimineshapeeditor.editor.title"));
        CustomShapes saved = ClientPatternCache.get();
        for (int i = 0; i < SLOTS; i++) {
            names[i] = saved.get(i).name();
            cells.add(new HashSet<>(saved.get(i).cells()));
            repeatFrom[i] = saved.get(i).repeatFrom();
            shiftRight[i] = saved.get(i).shiftRight();
            shiftUp[i] = saved.get(i).shiftUp();
            maxDepth[i] = saved.get(i).maxDepth();
            loopMask[i] = saved.get(i).loopLayers();
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        // slot tabs: green number = slot has a shape (shown in Ultimine's menu), grey = empty (hidden)
        int tabW = 22, tabGap = 2;
        int tabsX = (width - (SLOTS * tabW + (SLOTS - 1) * tabGap)) / 2;
        slotTabs.clear();
        for (int i = 0; i < SLOTS; i++) {
            final int s = i;
            Button b = Button.builder(Component.literal(String.valueOf(i + 1)), btn -> selectSlot(s))
                    .bounds(tabsX + i * (tabW + tabGap), 18, tabW, 16).build();
            b.active = i != slot;
            slotTabs.add(addRenderableWidget(b));
        }

        // name
        nameBox = new EditBox(font, (width - 160) / 2, 38, 160, 16, Component.translatable("ultimineshapeeditor.editor.name"));
        nameBox.setMaxLength(ShapePattern.MAX_NAME);
        nameBox.setHint(Component.translatable("ultimineshapeeditor.editor.name_hint", slot + 1).withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(names[slot]);
        nameBox.setResponder(s -> {
            if (!s.equals(names[slot])) {
                names[slot] = s;
                markDirty();
            }
        });
        addRenderableWidget(nameBox);

        // grid: centred, sized to fit between the name box and the bottom buttons, with side panels
        gridY = 60;
        int availH = height - gridY - 34;
        int availW = width - 2 * (PANEL + 14);
        cell = Mth.clamp(Math.min(availH, availW) / SIZE, 5, 18);
        gridW = cell * SIZE;
        gridX = (width - gridW) / 2;
        panelX = gridX + gridW + 10;

        // right panel: layer controls and bulk edits
        prevLayerBtn = addRenderableWidget(Button.builder(Component.literal("<"), b -> changeLayer(-1))
                .bounds(panelX, gridY, 18, 16).build());
        nextLayerBtn = addRenderableWidget(Button.builder(Component.literal(">"), b -> changeLayer(1))
                .bounds(panelX + PANEL - 18, gridY, 18, 16).build());
        int y = gridY + 20;
        repeatBtn = addRenderableWidget(Button.builder(repeatLabel(), b -> toggleRepeatHere())
                .bounds(panelX, y, PANEL, 16).build());
        inLoopBtn = addRenderableWidget(Button.builder(inLoopLabel(), b -> toggleInLoop())
                .bounds(panelX, y + 18, PANEL, 16)
                .tooltip(Tooltip.create(Component.translatable("ultimineshapeeditor.editor.in_loop_tip"))).build());
        y += 18;
        // shift rows: [-] "Shift → +1" [+]
        shiftButtons.clear();
        shiftButtons.add(addRenderableWidget(Button.builder(Component.literal("-"), b -> changeShift(-1, 0))
                .bounds(panelX, y + 18, 16, 16).build()));
        shiftButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> changeShift(1, 0))
                .bounds(panelX + PANEL - 16, y + 18, 16, 16).build()));
        shiftButtons.add(addRenderableWidget(Button.builder(Component.literal("-"), b -> changeShift(0, -1))
                .bounds(panelX, y + 36, 16, 16).build()));
        shiftButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> changeShift(0, 1))
                .bounds(panelX + PANEL - 16, y + 36, 16, 16).build()));
        // max depth row: [-] "Max depth: off" [+]   (shift-click = 10 at a time)
        Tooltip stepTip = Tooltip.create(Component.translatable("ultimineshapeeditor.editor.max_depth_tip"));
        depthMinusBtn = addRenderableWidget(Button.builder(Component.literal("-"), b -> changeMaxDepth(-1))
                .bounds(panelX, y + 54, 16, 16).tooltip(stepTip).build());
        depthPlusBtn = addRenderableWidget(Button.builder(Component.literal("+"), b -> changeMaxDepth(1))
                .bounds(panelX + PANEL - 16, y + 54, 16, 16).tooltip(stepTip).build());
        y += 76;

        // bulk edits, 2 x 2
        int half = (PANEL - 2) / 2;
        addRenderableWidget(Button.builder(Component.translatable("ultimineshapeeditor.editor.clear_layer"), b -> clearLayer())
                .bounds(panelX, y, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("ultimineshapeeditor.editor.fill_layer"), b -> fillLayer())
                .bounds(panelX + PANEL - half, y, half, 16).build());
        copyPrevBtn = addRenderableWidget(Button.builder(Component.translatable("ultimineshapeeditor.editor.copy_prev"), b -> copyPreviousLayer())
                .bounds(panelX, y + 18, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("ultimineshapeeditor.editor.reset"), b -> resetShape())
                .bounds(panelX + PANEL - half, y + 18, half, 16)
                .tooltip(Tooltip.create(Component.translatable("ultimineshapeeditor.editor.reset_tip"))).build());
        updateButtons();

        // bottom row
        int bw = 70, gap = 4;
        int bx = (width - (4 * bw + 3 * gap)) / 2, by = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("ultimineshapeeditor.editor.copy_code"), b -> copyCode())
                .bounds(bx, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ultimineshapeeditor.editor.paste_code"), b -> pasteCode())
                .bounds(bx + (bw + gap), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ultimineshapeeditor.editor.save"), b -> saveAndClose())
                .bounds(bx + 2 * (bw + gap), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(bx + 3 * (bw + gap), by, bw, 20).build());
    }

    // ------------------------------------------------------------------ editing

    private Set<Integer> current() {
        return cells.get(slot);
    }

    private void markDirty() {
        dirty[slot] = true;
        confirmDiscard = false;
    }

    private static boolean isOrigin(int right, int up, int depth) {
        return right == 0 && up == 0 && depth == 0;
    }

    private boolean isSet(int right, int up, int depth) {
        if (!ShapePattern.inBounds(right, up, depth)) return false; // off the grid (e.g. shift preview)
        return isOrigin(right, up, depth) || current().contains(ShapePattern.pack(right, up, depth));
    }

    private void setCell(int right, int up, boolean value) {
        if (isOrigin(right, up, layer)) return; // the mined block is always included
        int p = ShapePattern.pack(right, up, layer);
        boolean changed = value ? current().add(p) : current().remove(p);
        if (changed) markDirty();
    }

    private void selectSlot(int s) {
        slot = s;
        lastSlot = s;
        layer = 0;
        rebuildWidgets();
    }

    private void changeLayer(int delta) {
        layer = Mth.clamp(layer + delta, 0, LAYERS - 1);
        updateButtons();
    }

    private boolean repeats() {
        return repeatFrom[slot] != ShapePattern.NO_REPEAT;
    }

    /** Last layer of the repeating section (same rule as {@link ShapePattern#loopEnd()}). */
    private int loopEnd() {
        return Math.max(repeatFrom[slot], ShapePattern.lastLayer(current()));
    }

    private boolean inLoop(int l) {
        if (!repeats()) return false;
        if (l < repeatFrom[slot]) return ShapePattern.inLoopMask(loopMask[slot], l);
        return l <= loopEnd();
    }

    private Component inLoopLabel() {
        String key;
        if (!repeats()) key = "ultimineshapeeditor.editor.in_loop_na";
        else if (layer >= repeatFrom[slot]) key = layer <= loopEnd()
                ? "ultimineshapeeditor.editor.in_loop_always" : "ultimineshapeeditor.editor.in_loop_na";
        else key = ShapePattern.inLoopMask(loopMask[slot], layer)
                ? "ultimineshapeeditor.editor.in_loop_yes" : "ultimineshapeeditor.editor.in_loop_no";
        return Component.translatable(key);
    }

    /** For a layer before the repeat start: switch whether it is part of every repeat. */
    private void toggleInLoop() {
        if (!repeats() || layer >= repeatFrom[slot]) return;
        loopMask[slot] ^= 1 << layer;
        markDirty();
        updateButtons();
    }

    private Component repeatLabel() {
        return Component.translatable(repeatFrom[slot] == layer
                ? "ultimineshapeeditor.editor.repeat_stop" : "ultimineshapeeditor.editor.repeat_here");
    }

    /** Start the repeating section at the current layer, or turn repeating off if it already starts here. */
    private void toggleRepeatHere() {
        repeatFrom[slot] = repeatFrom[slot] == layer ? ShapePattern.NO_REPEAT : layer;
        // only layers before the repeat start can be switched into the loop
        loopMask[slot] = repeats() ? loopMask[slot] & ((1 << repeatFrom[slot]) - 1) : 0;
        markDirty();
        updateButtons();
    }

    private void changeShift(int dRight, int dUp) {
        shiftRight[slot] = ShapePattern.clampShift(shiftRight[slot] + dRight);
        shiftUp[slot] = ShapePattern.clampShift(shiftUp[slot] + dUp);
        markDirty();
        updateButtons();
    }

    /**
     * Steps the max depth: off -> (current drawn depth) -> ... ; stepping below 1 turns it off again.
     * Shift-click steps by 10.
     */
    private void changeMaxDepth(int dir) {
        int step = minecraft.hasShiftDown() ? 10 : 1;
        int md = maxDepth[slot];
        if (md == ShapePattern.NO_LIMIT) {
            if (dir < 0) return;
            md = ShapePattern.lastLayer(current()) + 1; // start at the depth that's drawn
        } else {
            md += dir * step;
            if (md < 1) md = ShapePattern.NO_LIMIT;
        }
        maxDepth[slot] = Math.min(md, ShapePattern.MAX_DEPTH_LIMIT);
        markDirty();
        updateButtons();
    }

    private boolean beyondMaxDepth(int l) {
        return maxDepth[slot] != ShapePattern.NO_LIMIT && l >= maxDepth[slot];
    }

    private void updateButtons() {
        if (repeatBtn == null) return;
        repeatBtn.setMessage(repeatLabel());
        inLoopBtn.setMessage(inLoopLabel());
        inLoopBtn.active = repeats() && layer < repeatFrom[slot];
        prevLayerBtn.active = layer > 0;
        nextLayerBtn.active = layer < LAYERS - 1;
        copyPrevBtn.active = layer > 0;
        int m = ShapePattern.MAX_SHIFT;
        shiftButtons.get(0).active = repeats() && shiftRight[slot] > -m;
        shiftButtons.get(1).active = repeats() && shiftRight[slot] < m;
        shiftButtons.get(2).active = repeats() && shiftUp[slot] > -m;
        shiftButtons.get(3).active = repeats() && shiftUp[slot] < m;
        for (int i = 0; i < slotTabs.size(); i++) {
            slotTabs.get(i).setMessage(Component.literal(String.valueOf(i + 1))
                    .withStyle(isDefined(i) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        depthMinusBtn.active = maxDepth[slot] != ShapePattern.NO_LIMIT;
        depthPlusBtn.active = maxDepth[slot] < ShapePattern.MAX_DEPTH_LIMIT;
    }

    /** The loop layers as short text, e.g. "1, 3-5". */
    private String loopLayersText() {
        List<Integer> layers = new ArrayList<>();
        for (int l = 0; l < LAYERS; l++) if (inLoop(l)) layers.add(l + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < layers.size(); ) {
            int j = i;
            while (j + 1 < layers.size() && layers.get(j + 1) == layers.get(j) + 1) j++;
            if (sb.length() > 0) sb.append(", ");
            sb.append(layers.get(i));
            if (j > i) sb.append('–').append(layers.get(j));
            i = j + 1;
        }
        return sb.toString();
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    private void clearLayer() {
        if (current().removeIf(p -> ShapePattern.depth(p) == layer)) markDirty();
        updateButtons();
    }

    private void fillLayer() {
        for (int u = -R; u <= R; u++) for (int r = -R; r <= R; r++) setCell(r, u, true);
        updateButtons();
    }

    private void copyPreviousLayer() {
        if (layer == 0) return;
        clearLayer();
        for (int p : List.copyOf(current())) {
            if (ShapePattern.depth(p) == layer - 1) setCell(ShapePattern.right(p), ShapePattern.up(p), true);
        }
        if (layer - 1 == 0) setCell(0, 0, true); // the (implicit) mined block counts as part of layer 1
        updateButtons();
    }

    /** Delete this slot's shape entirely (cells, name, repeat, shift, depth). Empty slots are hidden in Ultimine. */
    private void resetShape() {
        current().clear();
        names[slot] = "";
        repeatFrom[slot] = ShapePattern.NO_REPEAT;
        shiftRight[slot] = 0;
        shiftUp[slot] = 0;
        maxDepth[slot] = ShapePattern.NO_LIMIT;
        loopMask[slot] = 0;
        layer = 0;
        markDirty();
        rebuildWidgets(); // refresh the name box
    }

    private boolean isDefined(int s) {
        return !cells.get(s).isEmpty() || repeatFrom[s] != ShapePattern.NO_REPEAT;
    }

    private ShapePattern currentPattern() {
        return ShapePattern.sanitized(names[slot], current(), repeatFrom[slot], shiftRight[slot], shiftUp[slot], maxDepth[slot],
                loopMask[slot]);
    }

    private void copyCode() {
        minecraft.keyboardHandler.setClipboard(currentPattern().toShareCode());
        showStatus(Component.translatable("ultimineshapeeditor.editor.code_copied").withStyle(ChatFormatting.GREEN));
    }

    private void pasteCode() {
        Optional<ShapePattern> pasted = ShapePattern.fromShareCode(minecraft.keyboardHandler.getClipboard());
        if (pasted.isEmpty()) {
            showStatus(Component.translatable("ultimineshapeeditor.editor.code_invalid").withStyle(ChatFormatting.RED));
            return;
        }
        current().clear();
        current().addAll(pasted.get().cells());
        names[slot] = pasted.get().name();
        repeatFrom[slot] = pasted.get().repeatFrom();
        shiftRight[slot] = pasted.get().shiftRight();
        shiftUp[slot] = pasted.get().shiftUp();
        maxDepth[slot] = pasted.get().maxDepth();
        loopMask[slot] = pasted.get().loopLayers();
        layer = 0;
        markDirty();
        rebuildWidgets(); // refresh the name box
        showStatus(Component.translatable("ultimineshapeeditor.editor.code_pasted").withStyle(ChatFormatting.GREEN));
    }

    private void saveAndClose() {
        CustomShapes updated = ClientPatternCache.get();
        for (int i = 0; i < SLOTS; i++) {
            if (!dirty[i]) continue;
            ShapePattern p = ShapePattern.sanitized(names[i], cells.get(i), repeatFrom[i], shiftRight[i], shiftUp[i], maxDepth[i], loopMask[i]);
            ClientPacketDistributor.sendToServer(new UpdatePatternPayload(i, p));
            updated = updated.with(i, p);
        }
        ClientPatternCache.set(updated); // show new names right away; the server confirms with a sync
        Arrays.fill(dirty, false);
        super.onClose();
    }

    @Override
    public void onClose() {
        boolean anyDirty = false;
        for (boolean d : dirty) anyDirty |= d;
        if (anyDirty && !confirmDiscard) {
            confirmDiscard = true;
            showStatus(Component.translatable("ultimineshapeeditor.editor.discard_confirm").withStyle(ChatFormatting.GOLD));
            return;
        }
        super.onClose();
    }

    private void showStatus(Component c) {
        status = c;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    // ------------------------------------------------------------------ input

    /** @return {right, up} of the grid cell under the mouse, or null */
    @Nullable
    private int[] cellAt(double mx, double my) {
        if (mx < gridX || my < gridY || mx >= gridX + gridW || my >= gridY + gridW) return null;
        int col = (int) ((mx - gridX) / cell);
        int row = (int) ((my - gridY) / cell);
        return new int[]{col - R, R - row};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        int button = event.button();
        int[] c = cellAt(mx, my);
        if (c == null || (button != 0 && button != 1)) return false;
        setFocused(null); // stop typing into the name box
        paintValue = button == 0 && !isSet(c[0], c[1], layer);
        setCell(c[0], c[1], paintValue);
        updateButtons();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (paintValue != null) {
            int[] c = cellAt(event.x(), event.y());
            if (c != null) {
                setCell(c[0], c[1], paintValue);
                updateButtons();
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        paintValue = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY != 0 && cellAt(mx, my) != null) {
            changeLayer(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        centered(g, title, width / 2, 6, 0xFFFFFFFF);

        drawGrid(g, mx, my);
        drawBeyondOverlay(g);

        // layer label between the < > buttons, with a loop mark for repeating layers
        Component layerLabel = Component.translatable("ultimineshapeeditor.editor.layer", layer + 1, LAYERS)
                .append(inLoop(layer) ? Component.literal(" ⟳").withStyle(ChatFormatting.AQUA) : Component.empty());
        centered(g, layerLabel, panelX + PANEL / 2, gridY + 4, 0xFFFFFFFF);

        // shift values between their - + buttons
        int shiftColor = repeats() ? 0xFFFFFFFF : 0xFF707070;
        centered(g, Component.translatable("ultimineshapeeditor.editor.shift_right", signed(shiftRight[slot])),
                panelX + PANEL / 2, gridY + 60, shiftColor);
        centered(g, Component.translatable("ultimineshapeeditor.editor.shift_up", signed(shiftUp[slot])),
                panelX + PANEL / 2, gridY + 78, shiftColor);
        Component depthText = maxDepth[slot] == ShapePattern.NO_LIMIT
                ? Component.translatable("ultimineshapeeditor.editor.max_depth_off")
                : Component.translatable("ultimineshapeeditor.editor.max_depth", maxDepth[slot]);
        centered(g, depthText, panelX + PANEL / 2, gridY + 96, 0xFFFFFFFF);

        drawHelp(g);

        if (status != null && System.currentTimeMillis() < statusUntil) {
            centered(g, status, width / 2, height - 38, 0xFFFFFFFF);
        }
    }

    private void drawGrid(GuiGraphicsExtractor g, int mx, int my) {
        boolean loopLayer = inLoop(layer);
        if (loopLayer) g.fill(gridX - 3, gridY - 3, gridX + gridW + 3, gridY + gridW + 3, COL_LOOP);
        g.fill(gridX - 1, gridY - 1, gridX + gridW + 1, gridY + gridW + 1, 0xFF000000);
        int[] hover = cellAt(mx, my);
        int inset = Math.max(1, cell / 4);
        int dot = Math.max(1, cell / 3);
        int sr = shiftRight[slot], su = shiftUp[slot];
        boolean showNext = loopLayer && (sr != 0 || su != 0);

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int r = col - R, u = R - row;
                int x0 = gridX + col * cell, y0 = gridY + row * cell;
                int x1 = x0 + cell - 1, y1 = y0 + cell - 1;

                int bg = (r == 0 || u == 0) ? COL_AXIS : ((row + col) % 2 == 0 ? COL_CELL_A : COL_CELL_B);
                g.fill(x0, y0, x1, y1, bg);

                if (isOrigin(r, u, layer)) {
                    g.fill(x0, y0, x1, y1, COL_ORIGIN);
                } else if (isSet(r, u, layer)) {
                    g.fill(x0, y0, x1, y1, COL_SET);
                } else {
                    if (layer > 0 && isSet(r, u, layer - 1)) {
                        g.fill(x0 + inset, y0 + inset, x1 - inset, y1 - inset, COL_GHOST);
                    }
                    if (r == 0 && u == 0) { // where the mined block's column runs through this layer
                        g.fill(x0 + inset, y0 + inset, x1 - inset, y1 - inset, COL_ORIGIN_MARK);
                    }
                }

                // preview: this cell is where a cell of this layer lands in the next repeat
                if (showNext && isSet(r - sr, u - su, layer)) {
                    int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
                    g.fill(cx - dot / 2, cy - dot / 2, cx - dot / 2 + dot, cy - dot / 2 + dot, COL_NEXT);
                }

                if (hover != null && hover[0] == r && hover[1] == u) {
                    outline(g, x0, y0, cell - 1, cell - 1, 0xFFFFFFFF);
                }
            }
        }
    }

    private void drawBeyondOverlay(GuiGraphicsExtractor g) {
        if (!beyondMaxDepth(layer)) return;
        g.fill(gridX, gridY, gridX + gridW, gridY + gridW, 0xA0200000);
        int cy = gridY + gridW / 2 - 4;
        centered(g, Component.translatable("ultimineshapeeditor.editor.beyond_max_depth").withStyle(ChatFormatting.RED),
                gridX + gridW / 2, cy, 0xFFFFFFFF);
    }

    private void drawHelp(GuiGraphicsExtractor g) {
        int x = 8;
        int w = gridX - 18;
        if (w < 60) return; // screen too narrow for the help panel
        int y = gridY;

        // slot number and whether it shows up in Ultimine's menu
        g.text(font, Component.translatable("ultimineshapeeditor.editor.slot", slot + 1), x, y, 0xFFFFFFFF, true);
        y += 10;
        for (FormattedCharSequence line : font.split(isDefined(slot)
                ? Component.translatable("ultimineshapeeditor.editor.slot_shown").withStyle(ChatFormatting.GREEN)
                : Component.translatable("ultimineshapeeditor.editor.slot_hidden").withStyle(ChatFormatting.GRAY), w)) {
            g.text(font, line, x, y, 0xFFFFFFFF, true);
            y += 10;
        }
        y += 4;

        int blocks = current().size() + 1; // + the mined block
        g.text(font, Component.translatable(repeats() ? "ultimineshapeeditor.editor.blocks_repeat" : "ultimineshapeeditor.editor.blocks", blocks),
                x, y, 0xFFFFFFFF, true);
        y += 12;
        if (repeats()) {
            Component loop = Component.translatable("ultimineshapeeditor.editor.repeat_summary", loopLayersText(),
                    signed(shiftRight[slot]), signed(shiftUp[slot]));
            for (FormattedCharSequence line : font.split(loop.copy().withStyle(ChatFormatting.AQUA), w)) {
                g.text(font, line, x, y, 0xFFFFFFFF, true);
                y += 10;
            }
            y += 2;
        }
        if (maxDepth[slot] != ShapePattern.NO_LIMIT) {
            for (FormattedCharSequence line : font.split(Component.translatable("ultimineshapeeditor.editor.max_depth_summary", maxDepth[slot])
                    .withStyle(ChatFormatting.YELLOW), w)) {
                g.text(font, line, x, y, 0xFFFFFFFF, true);
                y += 10;
            }
            y += 2;
        }
        if (dirty[slot]) {
            g.text(font, Component.translatable("ultimineshapeeditor.editor.unsaved").withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFFFF, true);
            y += 12;
        }
        y += 4;
        for (FormattedCharSequence line : font.split(Component.translatable("ultimineshapeeditor.editor.help"), w)) {
            g.text(font, line, x, y, 0xFFA0A0A0, true);
            y += 10;
        }
    }

    /** Text centred on x (with shadow), like the old drawCenteredString. */
    private void centered(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.text(font, text, x - font.width(text) / 2, y, color, true);
    }

    /** 1px rectangle outline. */
    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
