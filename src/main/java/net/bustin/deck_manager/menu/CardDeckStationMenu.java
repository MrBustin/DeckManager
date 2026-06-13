package net.bustin.deck_manager.menu;

import iskallia.vault.item.CardDeckItem;
import iskallia.vault.item.CardItem;
import net.bustin.deck_manager.blocks.ModBlocks;
import net.bustin.deck_manager.blocks.entity.custom.CardDeckStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public class CardDeckStationMenu extends AbstractContainerMenu {
    public static final int DECK_SLOT_X = 6;
    public static final int DECK_SLOT_Y = 11;
    public static final int PLAYER_INVENTORY_X = 52;
    public static final int PLAYER_INVENTORY_Y = 202;
    public static final int PLAYER_HOTBAR_Y = 260;

    private final ContainerLevelAccess access;
    private final BlockPos blockPos;

    public CardDeckStationMenu(int containerId, Inventory inventory, FriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public CardDeckStationMenu(int containerId, Inventory inventory, BlockPos blockPos) {
        super(ModMenuTypes.CARD_DECK_STATION_MENU.get(), containerId);
        this.blockPos = blockPos;
        this.access = ContainerLevelAccess.create(inventory.player.level, blockPos);

        ItemStackHandler stationItems = getStationItems(inventory, blockPos);
        addStationSlots(stationItems);
        addPlayerInventory(inventory);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CARD_DECK_STATION.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack movedStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();
        movedStack = slotStack.copy();

        int stationEnd = CardDeckStationBlockEntity.TOTAL_SLOTS;
        int inventoryEnd = stationEnd + 27;
        int hotbarEnd = inventoryEnd + 9;

        if (index < stationEnd) {
            if (!this.moveItemStackTo(slotStack, stationEnd, hotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (slotStack.getItem() instanceof CardDeckItem) {
            if (!this.moveItemStackTo(slotStack, CardDeckStationBlockEntity.DECK_SLOT,
                    CardDeckStationBlockEntity.DECK_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotStack.getItem() instanceof CardItem) {
            if (!this.moveItemStackTo(slotStack, CardDeckStationBlockEntity.CARD_STORAGE_START,
                    stationEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < inventoryEnd) {
            if (!this.moveItemStackTo(slotStack, inventoryEnd, hotbarEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(slotStack, stationEnd, inventoryEnd, false)) {
            return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotStack.getCount() == movedStack.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, slotStack);
        return movedStack;
    }

    private ItemStackHandler getStationItems(Inventory inventory, BlockPos blockPos) {
        BlockEntity blockEntity = inventory.player.level.getBlockEntity(blockPos);
        if (blockEntity instanceof CardDeckStationBlockEntity station) {
            return station.getItems();
        }
        return new ItemStackHandler(CardDeckStationBlockEntity.TOTAL_SLOTS);
    }

    private void addStationSlots(ItemStackHandler stationItems) {
        this.addSlot(new SlotItemHandler(stationItems, CardDeckStationBlockEntity.DECK_SLOT, DECK_SLOT_X, DECK_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CardDeckItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = CardDeckStationBlockEntity.CARD_STORAGE_START + column + row * 9;
                this.addSlot(new SlotItemHandler(stationItems, slot, -10000, -10000) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof CardItem;
                    }
                });
            }
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, PLAYER_INVENTORY_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }
}
