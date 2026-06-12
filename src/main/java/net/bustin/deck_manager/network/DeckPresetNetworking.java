package net.bustin.deck_manager.network;

import iskallia.vault.core.card.Card;
import iskallia.vault.core.card.CardDeck;
import iskallia.vault.container.inventory.CardDeckContainer;
import iskallia.vault.item.CardDeckItem;
import iskallia.vault.item.CardItem;
import net.bustin.deck_manager.blocks.entity.custom.CardDeckStationBlockEntity;
import net.bustin.deck_manager.blocks.entity.custom.CardDeckStationBlockEntity.DeckPreset;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DeckPresetNetworking {
    private static final int MAX_PRESET_NAME_LENGTH = 32;

    public static void sendPresets(ServerPlayer player, CardDeckStationBlockEntity station) {
        List<SyncDeckPresetsS2CPacket.PresetSummary> summaries = station.getPresets().stream()
                .map(preset -> {
                    LoadPlan loadPlan = planLoad(station, preset);
                    return SyncDeckPresetsS2CPacket.PresetSummary.fromPreset(preset, loadPlan,
                            createPresetPreviewStacks(preset));
                })
                .toList();
        ModNetworks.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncDeckPresetsS2CPacket(station.getBlockPos(), summaries));
    }

    public static Optional<CardDeckStationBlockEntity> findStation(ServerPlayer player, BlockPos pos) {
        if (player.level == null || !player.level.isLoaded(pos)) {
            return Optional.empty();
        }

        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return Optional.empty();
        }

        if (player.level.getBlockEntity(pos) instanceof CardDeckStationBlockEntity station) {
            return Optional.of(station);
        }
        return Optional.empty();
    }

    public static void saveStationDeckPreset(ServerPlayer player, CardDeckStationBlockEntity station, String rawName) {
        String presetName = sanitizePresetName(rawName);
        if (presetName.isEmpty()) {
            presetName = nextPresetName(station);
        }

        ItemStack stack = station.getItems().getStackInSlot(CardDeckStationBlockEntity.DECK_SLOT);
        if (stack.isEmpty()) {
            player.displayClientMessage(new TextComponent("Put a Vault Hunters card deck in the station deck slot."), true);
            return;
        }

        Optional<CardDeck> deck = CardDeckItem.getCardDeck(stack);
        if (deck.isEmpty()) {
            player.displayClientMessage(new TextComponent("That deck has no saved card layout yet."), true);
            return;
        }

        Optional<CompoundTag> deckData = deck.get().writeNbt();
        if (deckData.isEmpty()) {
            player.displayClientMessage(new TextComponent("Could not read that deck layout."), true);
            return;
        }

        List<ItemStack> cardStacks = createCardStacks(deck.get().getCards());
        if (!canInsertCards(station.getItems(), cardStacks)) {
            player.displayClientMessage(new TextComponent("Not enough station card storage for this deck."), true);
            return;
        }

        if (!canClearStationDeck(stack)) {
            player.displayClientMessage(new TextComponent("Could not empty the station deck."), true);
            return;
        }

        insertCards(station.getItems(), cardStacks);
        if (!clearStationDeck(stack)) {
            player.displayClientMessage(new TextComponent("Could not empty the station deck."), true);
            return;
        }

        station.upsertPreset(new DeckPreset(
                presetName,
                CardDeckItem.getId(stack),
                stack.getHoverName().getString(),
                deckData.get(),
                System.currentTimeMillis()
        ));
        station.getItems().setStackInSlot(CardDeckStationBlockEntity.DECK_SLOT, stack);
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(new TextComponent("Saved preset and moved cards into station storage: " + presetName), true);
        sendPresets(player, station);
    }

    public static void loadPresetToStationDeck(ServerPlayer player, CardDeckStationBlockEntity station, String rawName) {
        String presetName = sanitizePresetName(rawName);
        if (presetName.isEmpty()) {
            player.displayClientMessage(new TextComponent("Select a preset first."), true);
            return;
        }

        Optional<DeckPreset> preset = station.getPreset(presetName);
        if (preset.isEmpty()) {
            player.displayClientMessage(new TextComponent("Preset not found: " + presetName), true);
            return;
        }

        ItemStack stack = station.getItems().getStackInSlot(CardDeckStationBlockEntity.DECK_SLOT);
        if (stack.isEmpty()) {
            player.displayClientMessage(new TextComponent("Put a compatible Vault Hunters card deck in the station deck slot."), true);
            return;
        }

        String targetDeckId = CardDeckItem.getId(stack);
        if (!preset.get().sourceDeckId().equals(targetDeckId)) {
            player.displayClientMessage(new TextComponent("Preset is for deck '" + preset.get().sourceDeckId()
                    + "', not '" + targetDeckId + "'."), true);
            return;
        }

        Optional<CardDeck> targetDeck = CardDeckItem.getCardDeck(stack);
        if (targetDeck.isEmpty()) {
            player.displayClientMessage(new TextComponent("Target deck has no card deck data."), true);
            return;
        }

        LoadPlan plan = planLoad(station, preset.get());
        if (!plan.hasDeck()) {
            player.displayClientMessage(new TextComponent("Put a compatible Vault Hunters card deck in the station deck slot."), true);
            return;
        }
        if (plan.missingCards() > 0) {
            player.displayClientMessage(new TextComponent("Station storage is missing " + plan.missingCards()
                    + " of " + plan.requiredCards() + " cards for this preset."), true);
            return;
        }
        if (!plan.canStoreReturnedCards()) {
            player.displayClientMessage(new TextComponent("Not enough station card storage to return "
                    + plan.currentDeckCards() + " current deck cards."), true);
            return;
        }

        ListTag presetCards = preset.get().deckData().getList("cards", 10);
        if (!canReplaceDeckCards(stack, presetCards)) {
            player.displayClientMessage(new TextComponent("Could not load that preset onto this deck."), true);
            return;
        }

        for (int slot : plan.matchedStorageSlots()) {
            station.getItems().extractItem(slot, 1, false);
        }
        insertCards(station.getItems(), plan.currentCardStacks());

        if (!replaceDeckCards(stack, presetCards)) {
            player.displayClientMessage(new TextComponent("Could not load that preset onto this deck."), true);
            return;
        }

        station.getItems().setStackInSlot(CardDeckStationBlockEntity.DECK_SLOT, stack);
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(new TextComponent("Loaded preset and swapped physical cards: " + preset.get().name()), true);
        sendPresets(player, station);
    }

    public static LoadPlan planLoad(CardDeckStationBlockEntity station, DeckPreset preset) {
        MatchResult match = findMatchingStoredCards(station.getItems(), preset.deckData());
        ItemStack stack = station.getItems().getStackInSlot(CardDeckStationBlockEntity.DECK_SLOT);
        if (stack.isEmpty()) {
            return new LoadPlan(false, false, match.requiredCards(), match.matchedStorageSlots().size(),
                    match.missingCards(), 0, false, List.of(), match.matchedStorageSlots(), match.cardAvailability());
        }

        boolean compatibleDeck = preset.sourceDeckId().equals(CardDeckItem.getId(stack));
        Optional<CardDeck> targetDeck = CardDeckItem.getCardDeck(stack);
        if (!compatibleDeck || targetDeck.isEmpty()) {
            return new LoadPlan(true, compatibleDeck, match.requiredCards(), match.matchedStorageSlots().size(),
                    match.missingCards(), 0, false, List.of(), match.matchedStorageSlots(), match.cardAvailability());
        }

        List<ItemStack> currentCardStacks = createCardStacks(targetDeck.get().getCards());
        boolean canStoreReturnedCards = match.missingCards() == 0
                && canSwapCards(station.getItems(), match.matchedStorageSlots(), currentCardStacks);
        return new LoadPlan(true, true, match.requiredCards(), match.matchedStorageSlots().size(),
                match.missingCards(), currentCardStacks.size(), canStoreReturnedCards,
                currentCardStacks, match.matchedStorageSlots(), match.cardAvailability());
    }

    public static List<ItemStack> createPresetPreviewStacks(DeckPreset preset) {
        List<ItemStack> stacks = new ArrayList<>();
        for (CompoundTag cardTag : getRequiredCardTags(preset.deckData())) {
            try {
                Card card = new Card();
                card.readNbt(cardTag);
                if (!card.getEntries().isEmpty()) {
                    stacks.add(CardItem.create(card));
                }
            } catch (RuntimeException ignored) {
            }
        }
        return stacks;
    }

    private static List<ItemStack> createCardStacks(Map<?, ?> cards) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Object card : cards.values()) {
            if (card instanceof iskallia.vault.core.card.Card vaultCard && !vaultCard.getEntries().isEmpty()) {
                stacks.add(CardItem.create(vaultCard));
            }
        }
        return stacks;
    }

    private static boolean canInsertCards(ItemStackHandler handler, List<ItemStack> stacks) {
        ItemStackHandler simulated = copyHandler(handler);
        for (ItemStack stack : stacks) {
            if (!insertCard(simulated, stack.copy(), false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void insertCards(ItemStackHandler handler, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            insertCard(handler, stack.copy(), false);
        }
    }

    private static ItemStack insertCard(ItemStackHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int slot = CardDeckStationBlockEntity.CARD_STORAGE_START;
             slot < CardDeckStationBlockEntity.TOTAL_SLOTS && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private static boolean canSwapCards(ItemStackHandler handler, List<Integer> matchedStorageSlots,
                                        List<ItemStack> currentCardStacks) {
        ItemStackHandler simulated = copyHandler(handler);
        for (int slot : matchedStorageSlots) {
            simulated.extractItem(slot, 1, false);
        }
        return canInsertCards(simulated, currentCardStacks);
    }

    private static MatchResult findMatchingStoredCards(ItemStackHandler handler, CompoundTag deckData) {
        List<CompoundTag> requiredCards = getRequiredCardTags(deckData);
        List<Integer> matchedSlots = new ArrayList<>();
        List<Boolean> cardAvailability = new ArrayList<>();
        Set<Integer> usedSlots = new HashSet<>();
        int missingCards = 0;

        for (CompoundTag requiredCard : requiredCards) {
            int slot = findMatchingStoredCard(handler, requiredCard, usedSlots);
            if (slot < 0) {
                missingCards++;
                cardAvailability.add(false);
                continue;
            }
            matchedSlots.add(slot);
            usedSlots.add(slot);
            cardAvailability.add(true);
        }

        return new MatchResult(requiredCards.size(), matchedSlots, missingCards, cardAvailability);
    }

    private static int findMatchingStoredCard(ItemStackHandler handler, CompoundTag requiredCard, Set<Integer> usedSlots) {
        for (int slot = CardDeckStationBlockEntity.CARD_STORAGE_START;
             slot < CardDeckStationBlockEntity.TOTAL_SLOTS; slot++) {
            if (usedSlots.contains(slot)) {
                continue;
            }

            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() instanceof CardItem && cardMatches(stack, requiredCard)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean cardMatches(ItemStack stack, CompoundTag requiredCard) {
        try {
            Optional<CompoundTag> storedCard = CardItem.getCard(stack).writeNbt();
            Optional<CompoundTag> normalizedRequiredCard = normalizeCardTag(requiredCard);
            return storedCard
                    .flatMap(stored -> normalizeCardTag(stored)
                            .map(normalizedStored -> normalizedStored.equals(normalizedRequiredCard.orElse(null))))
                    .orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static List<CompoundTag> getRequiredCardTags(CompoundTag deckData) {
        List<CompoundTag> requiredCards = new ArrayList<>();
        ListTag cards = deckData.getList("cards", 10);
        for (int i = 0; i < cards.size(); i++) {
            CompoundTag card = cards.getCompound(i).getCompound("card");
            if (hasUsableCardEntries(card)) {
                requiredCards.add(card);
            }
        }
        return requiredCards;
    }

    private static boolean hasUsableCardEntries(CompoundTag cardTag) {
        return normalizeCardTag(cardTag)
                .map(normalized -> !normalized.getList("entries", 10).isEmpty())
                .orElse(false);
    }

    private static Optional<CompoundTag> normalizeCardTag(CompoundTag cardTag) {
        try {
            Card card = new Card();
            card.readNbt(cardTag);
            if (card.getEntries().isEmpty()) {
                return Optional.empty();
            }
            return card.writeNbt();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean clearStationDeck(ItemStack stack) {
        try {
            CardDeckContainer container = new CardDeckContainer(stack);
            for (Integer slot : container.getSlotMapping().keySet()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            container.setChanged();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean canClearStationDeck(ItemStack stack) {
        try {
            new CardDeckContainer(stack);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean replaceDeckCards(ItemStack stack, ListTag cards) {
        Optional<CardDeck> mergedDeck = createDeckWithCards(stack, cards);
        if (mergedDeck.isEmpty()) {
            return false;
        }

        CardDeckItem.setCardDeck(stack, mergedDeck.get());
        return true;
    }

    private static boolean canReplaceDeckCards(ItemStack stack, ListTag cards) {
        return createDeckWithCards(stack, cards).isPresent();
    }

    private static Optional<CardDeck> createDeckWithCards(ItemStack stack, ListTag cards) {
        Optional<CardDeck> deck = CardDeckItem.getCardDeck(stack);
        if (deck.isEmpty()) {
            return Optional.empty();
        }

        Optional<CompoundTag> deckData = deck.get().writeNbt();
        if (deckData.isEmpty()) {
            return Optional.empty();
        }

        try {
            CompoundTag mergedDeckData = deckData.get().copy();
            mergedDeckData.put("cards", cards.copy());

            CardDeck mergedDeck = new CardDeck();
            mergedDeck.readNbt(mergedDeckData);
            return Optional.of(mergedDeck);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static ItemStackHandler copyHandler(ItemStackHandler handler) {
        ItemStackHandler copy = new ItemStackHandler(handler.getSlots());
        copy.deserializeNBT(handler.serializeNBT());
        return copy;
    }

    private static String sanitizePresetName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() > MAX_PRESET_NAME_LENGTH) {
            return name.substring(0, MAX_PRESET_NAME_LENGTH);
        }
        return name;
    }

    private static String nextPresetName(CardDeckStationBlockEntity station) {
        int presetNumber = 1;
        while (station.getPreset("Preset " + presetNumber).isPresent()) {
            presetNumber++;
        }
        return "Preset " + presetNumber;
    }

    private record MatchResult(int requiredCards, List<Integer> matchedStorageSlots, int missingCards,
                               List<Boolean> cardAvailability) {
    }

    public record LoadPlan(boolean hasDeck, boolean compatibleDeck, int requiredCards, int availableCards,
                           int missingCards, int currentDeckCards, boolean canStoreReturnedCards,
                           List<ItemStack> currentCardStacks, List<Integer> matchedStorageSlots,
                           List<Boolean> cardAvailability) {
        public boolean canLoad() {
            return hasDeck && compatibleDeck && missingCards == 0 && canStoreReturnedCards;
        }
    }

    private DeckPresetNetworking() {
    }
}
