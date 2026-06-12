package net.bustin.deck_manager.blocks.entity;

import net.bustin.deck_manager.DeckManager;
import net.bustin.deck_manager.blocks.ModBlocks;
import net.bustin.deck_manager.blocks.entity.custom.CardDeckStationBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, DeckManager.MOD_ID);

    public static final RegistryObject<BlockEntityType<CardDeckStationBlockEntity>> CARD_DECK_STATION =
            BLOCK_ENTITIES.register("card_deck_station",
                    () -> BlockEntityType.Builder.of(
                            CardDeckStationBlockEntity::new,
                            ModBlocks.CARD_DECK_STATION.get()
                    ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}

