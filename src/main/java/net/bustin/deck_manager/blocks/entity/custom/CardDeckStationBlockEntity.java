package net.bustin.deck_manager.blocks.entity.custom;

import iskallia.vault.core.card.Card;
import net.bustin.deck_manager.blocks.entity.ModBlockEntities;
import net.bustin.deck_manager.menu.CardDeckStationMenu;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CardDeckStationBlockEntity extends BlockEntity implements MenuProvider {
    private static final String PRESETS_KEY = "Presets";
    private static final String INVENTORY_KEY = "Inventory";
    private static final TextComponent TITLE = new TextComponent("Card Deck Station");
    public static final int DECK_SLOT = 0;
    public static final int CARD_STORAGE_START = 1;
    public static final int CARD_STORAGE_SLOTS = 36;
    public static final int TOTAL_SLOTS = CARD_STORAGE_START + CARD_STORAGE_SLOTS;

    private final List<DeckPreset> presets = new ArrayList<>();
    private final ItemStackHandler items = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            markStorageChanged();
        }
    };
    private final LazyOptional<ItemStackHandler> itemHandler = LazyOptional.of(() -> items);

    public CardDeckStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARD_DECK_STATION.get(), pos, state);
    }

    public List<DeckPreset> getPresets() {
        return Collections.unmodifiableList(presets);
    }

    public Optional<DeckPreset> getPreset(String name) {
        return presets.stream()
                .filter(preset -> preset.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public void upsertPreset(DeckPreset preset) {
        removePreset(preset.name());
        presets.add(preset.copy());
        markStorageChanged();
    }

    public boolean removePreset(String name) {
        boolean removed = presets.removeIf(preset -> preset.name().equalsIgnoreCase(name));
        if (removed) {
            markStorageChanged();
        }
        return removed;
    }

    public void clearPresets() {
        if (!presets.isEmpty()) {
            presets.clear();
            markStorageChanged();
        }
    }

    public ItemStackHandler getItems() {
        return items;
    }

    private void markStorageChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CardDeckStationMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        ListTag presetList = new ListTag();
        for (DeckPreset preset : presets) {
            presetList.add(preset.save());
        }
        tag.put(PRESETS_KEY, presetList);
        tag.put(INVENTORY_KEY, items.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        presets.clear();
        ListTag presetList = tag.getList(PRESETS_KEY, 10);
        for (int i = 0; i < presetList.size(); i++) {
            presets.add(DeckPreset.load(presetList.getCompound(i)));
        }

        if (tag.contains(INVENTORY_KEY)) {
            items.deserializeNBT(tag.getCompound(INVENTORY_KEY));
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return itemHandler.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    public record DeckPreset(String name, String sourceDeckId, String sourceDeckName,
                             CompoundTag deckData, long createdAt) {
        private static final String NAME_KEY = "Name";
        private static final String SOURCE_DECK_ID_KEY = "SourceDeckId";
        private static final String SOURCE_DECK_NAME_KEY = "SourceDeckName";
        private static final String DECK_DATA_KEY = "DeckData";
        private static final String CREATED_AT_KEY = "CreatedAt";

        public DeckPreset {
            deckData = deckData == null ? new CompoundTag() : deckData.copy();
        }

        public DeckPreset copy() {
            return new DeckPreset(name, sourceDeckId, sourceDeckName, deckData, createdAt);
        }

        public int cardCount() {
            int count = 0;
            ListTag cards = deckData.getList("cards", 10);
            for (int i = 0; i < cards.size(); i++) {
                CompoundTag card = cards.getCompound(i).getCompound("card");
                if (hasUsableCardEntries(card)) {
                    count++;
                }
            }
            return count;
        }

        private static boolean hasUsableCardEntries(CompoundTag cardTag) {
            try {
                Card card = new Card();
                card.readNbt(cardTag);
                return !card.getEntries().isEmpty();
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(NAME_KEY, name);
            tag.putString(SOURCE_DECK_ID_KEY, sourceDeckId);
            tag.putString(SOURCE_DECK_NAME_KEY, sourceDeckName);
            tag.put(DECK_DATA_KEY, deckData.copy());
            tag.putLong(CREATED_AT_KEY, createdAt);
            return tag;
        }

        private static DeckPreset load(CompoundTag tag) {
            return new DeckPreset(
                    tag.getString(NAME_KEY),
                    tag.getString(SOURCE_DECK_ID_KEY),
                    tag.getString(SOURCE_DECK_NAME_KEY),
                    tag.getCompound(DECK_DATA_KEY),
                    tag.getLong(CREATED_AT_KEY)
            );
        }
    }
}
