package net.bustin.deck_manager.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RequestDeckPresetsC2SPacket(BlockPos pos) {
    public static void encode(RequestDeckPresetsC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
    }

    public static RequestDeckPresetsC2SPacket decode(FriendlyByteBuf buffer) {
        return new RequestDeckPresetsC2SPacket(buffer.readBlockPos());
    }

    public static void handle(RequestDeckPresetsC2SPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            DeckPresetNetworking.findStation(player, message.pos)
                    .ifPresent(station -> DeckPresetNetworking.sendPresets(player, station));
        });
        context.setPacketHandled(true);
    }
}
