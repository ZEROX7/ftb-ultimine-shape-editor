package io.github.zerox7.ultimineshapeeditor.custom;

/**
 * The local player's shapes as last sent by the server. Only used on the client, for showing names
 * in Ultimine's shape menu and for filling the editor. Deliberately has no client-only imports,
 * so it is safe to reference from common code.
 */
public final class ClientPatternCache {
    private static volatile CustomShapes shapes = CustomShapes.empty();

    private ClientPatternCache() {}

    public static CustomShapes get() {
        return shapes;
    }

    public static void set(CustomShapes value) {
        shapes = value;
    }
}
