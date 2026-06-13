package net.bustin.deck_manager.network;

import net.bustin.deck_manager.blocks.entity.custom.CardDeckStationBlockEntity.DeckPreset;
import net.bustin.deck_manager.network.DeckPresetNetworking.LoadPlan;
import net.bustin.deck_manager.network.client.ClientPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record SyncDeckPresetsS2CPacket(BlockPos pos, List<PresetSummary> presets) {
    public static void encode(SyncDeckPresetsS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeVarInt(message.presets.size());
        for (PresetSummary preset : message.presets) {
            buffer.writeUtf(preset.name());
            buffer.writeUtf(preset.sourceDeckId());
            buffer.writeUtf(preset.sourceDeckName());
            buffer.writeVarInt(preset.cardCount());
            buffer.writeVarInt(preset.availableCards());
            buffer.writeVarInt(preset.missingCards());
            buffer.writeVarInt(preset.currentDeckCards());
            buffer.writeBoolean(preset.hasDeck());
            buffer.writeBoolean(preset.compatibleDeck());
            buffer.writeBoolean(preset.canStoreReturnedCards());
            buffer.writeBoolean(preset.canLoad());
            buffer.writeLong(preset.createdAt());
            buffer.writeVarInt(preset.layoutRows().size());
            for (String row : preset.layoutRows()) {
                buffer.writeUtf(row);
            }
            buffer.writeVarInt(preset.previewCards().size());
            for (int previewIndex = 0; previewIndex < preset.previewCards().size(); previewIndex++) {
                PreviewCard previewCard = preset.previewCards().get(previewIndex);
                buffer.writeItem(previewCard.stack());
                buffer.writeVarInt(previewCard.x());
                buffer.writeVarInt(previewCard.y());
                buffer.writeBoolean(previewIndex < preset.previewAvailable().size()
                        && preset.previewAvailable().get(previewIndex));
            }
        }
    }

    public static SyncDeckPresetsS2CPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int size = buffer.readVarInt();
        List<PresetSummary> presets = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = buffer.readUtf();
            String sourceDeckId = buffer.readUtf();
            String sourceDeckName = buffer.readUtf();
            int cardCount = buffer.readVarInt();
            int availableCards = buffer.readVarInt();
            int missingCards = buffer.readVarInt();
            int currentDeckCards = buffer.readVarInt();
            boolean hasDeck = buffer.readBoolean();
            boolean compatibleDeck = buffer.readBoolean();
            boolean canStoreReturnedCards = buffer.readBoolean();
            boolean canLoad = buffer.readBoolean();
            long createdAt = buffer.readLong();
            int layoutSize = buffer.readVarInt();
            List<String> layoutRows = new ArrayList<>(layoutSize);
            for (int layoutIndex = 0; layoutIndex < layoutSize; layoutIndex++) {
                layoutRows.add(buffer.readUtf());
            }
            int previewSize = buffer.readVarInt();
            List<PreviewCard> previewCards = new ArrayList<>(previewSize);
            List<Boolean> previewAvailable = new ArrayList<>(previewSize);
            for (int cardIndex = 0; cardIndex < previewSize; cardIndex++) {
                ItemStack stack = buffer.readItem();
                int cardX = buffer.readVarInt();
                int cardY = buffer.readVarInt();
                previewCards.add(new PreviewCard(stack, cardX, cardY));
                previewAvailable.add(buffer.readBoolean());
            }
            presets.add(new PresetSummary(
                    name,
                    sourceDeckId,
                    sourceDeckName,
                    cardCount,
                    availableCards,
                    missingCards,
                    currentDeckCards,
                    hasDeck,
                    compatibleDeck,
                    canStoreReturnedCards,
                    canLoad,
                    createdAt,
                    layoutRows,
                    previewCards,
                    previewAvailable
            ));
        }
        return new SyncDeckPresetsS2CPacket(pos, presets);
    }

    public static void handle(SyncDeckPresetsS2CPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.handleSyncDeckPresets(message)));
        context.setPacketHandled(true);
    }

    public record PresetSummary(String name, String sourceDeckId, String sourceDeckName, int cardCount,
                                int availableCards, int missingCards, int currentDeckCards,
                                boolean hasDeck, boolean compatibleDeck, boolean canStoreReturnedCards,
                                boolean canLoad, long createdAt, List<String> layoutRows,
                                List<PreviewCard> previewCards,
                                List<Boolean> previewAvailable) {
        public static PresetSummary fromPreset(DeckPreset preset, LoadPlan loadPlan,
                                               List<PreviewCard> previewCards, List<String> layoutRows) {
            return new PresetSummary(
                    preset.name(),
                    preset.sourceDeckId(),
                    preset.sourceDeckName(),
                    preset.cardCount(),
                    loadPlan.availableCards(),
                    loadPlan.missingCards(),
                    loadPlan.currentDeckCards(),
                    loadPlan.hasDeck(),
                    loadPlan.compatibleDeck(),
                    loadPlan.canStoreReturnedCards(),
                    loadPlan.canLoad(),
                    preset.createdAt(),
                    layoutRows,
                    previewCards,
                    loadPlan.cardAvailability()
            );
        }
    }

    public record PreviewCard(ItemStack stack, int x, int y) {
    }
}
