package io.github.zerox7.ultimineshapeeditor.net;

import io.github.zerox7.ultimineshapeeditor.custom.ClientPatternCache;
import io.github.zerox7.ultimineshapeeditor.custom.CustomShapes;
import io.github.zerox7.ultimineshapeeditor.custom.ModAttachments;
import io.github.zerox7.ultimineshapeeditor.custom.ShapePattern;
import io.github.zerox7.ultimineshapeeditor.custom.ShapeVisibility;
import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbultimine.FTBUltimine;
import dev.ftb.mods.ftbultimine.FTBUltiminePlayerData;
import dev.ftb.mods.ftbultimine.net.SendShapePacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    /** Mod bus. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(UpdatePatternPayload.TYPE, UpdatePatternPayload.STREAM_CODEC, ModNetwork::handleUpdate);
        registrar.playToClient(SyncPatternsPayload.TYPE, SyncPatternsPayload.STREAM_CODEC, ModNetwork::handleSync);
    }

    /** Game bus: give the client its saved shapes when it joins. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncPatternsPayload(player.getData(ModAttachments.CUSTOM_SHAPES)));
    }

    private static void handleUpdate(UpdatePatternPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            int slot = payload.slot();
            if (slot < 0 || slot >= CustomShapes.SLOTS) return;
            // never trust the client: re-sanitize (the stream codec already does, this is belt and braces)
            ShapePattern in = payload.pattern();
            ShapePattern pattern = ShapePattern.sanitized(in.name(), in.cells(), in.repeatFrom(), in.shiftRight(), in.shiftUp(), in.maxDepth());
            CustomShapes updated = player.getData(ModAttachments.CUSTOM_SHAPES).with(slot, pattern);
            player.setData(ModAttachments.CUSTOM_SHAPES, updated);
            sync(player);
            moveOffHiddenShape(player, updated);
        });
    }

    /** If the player just emptied the slot they have selected, switch them to the next visible shape. */
    private static void moveOffHiddenShape(ServerPlayer player, CustomShapes shapes) {
        FTBUltiminePlayerData data = FTBUltimine.getInstance().getOrCreatePlayerData(player);
        if (ShapeVisibility.isHidden(shapes, data.getCurrentShapeIndex())) {
            data.cycleShape(true); // the cycle mixin skips hidden slots
            data.clearCache();
            NetworkManager.sendToPlayer(player, SendShapePacket.adjustShapeOnly(data.getCurrentShapeIndex()));
        }
    }

    private static void handleSync(SyncPatternsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPatternCache.set(payload.shapes()));
    }
}
