package net.bustin.deck_manager.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DepositDeckCardsToPresetC2SPacket(BlockPos pos, String presetName) {
    public static void encode(DepositDeckCardsToPresetC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeUtf(message.presetName);
    }

    public static DepositDeckCardsToPresetC2SPacket decode(FriendlyByteBuf buffer) {
        return new DepositDeckCardsToPresetC2SPacket(buffer.readBlockPos(), buffer.readUtf());
    }

    public static void handle(DepositDeckCardsToPresetC2SPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            DeckPresetNetworking.findStation(player, message.pos)
                    .ifPresent(station -> DeckPresetNetworking.depositStationDeckCardsToPreset(
                            player, station, message.presetName));
        });
        context.setPacketHandled(true);
    }
}
