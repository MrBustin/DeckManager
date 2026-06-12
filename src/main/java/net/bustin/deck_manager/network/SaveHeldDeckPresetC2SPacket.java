package net.bustin.deck_manager.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SaveHeldDeckPresetC2SPacket(BlockPos pos, String presetName) {
    public static void encode(SaveHeldDeckPresetC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeUtf(message.presetName);
    }

    public static SaveHeldDeckPresetC2SPacket decode(FriendlyByteBuf buffer) {
        return new SaveHeldDeckPresetC2SPacket(buffer.readBlockPos(), buffer.readUtf());
    }

    public static void handle(SaveHeldDeckPresetC2SPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            DeckPresetNetworking.findStation(player, message.pos)
                    .ifPresent(station -> DeckPresetNetworking.saveStationDeckPreset(player, station, message.presetName));
        });
        context.setPacketHandled(true);
    }
}
