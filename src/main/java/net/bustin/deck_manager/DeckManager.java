package net.bustin.deck_manager;

import com.mojang.logging.LogUtils;
import net.bustin.deck_manager.blocks.ModBlocks;
import net.bustin.deck_manager.blocks.entity.ModBlockEntities;
import net.bustin.deck_manager.items.ModItems;
import net.bustin.deck_manager.menu.ModMenuTypes;
import net.bustin.deck_manager.network.ModNetworks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(DeckManager.MOD_ID)
public class DeckManager {
    public static final String MOD_ID = "deck_manager";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DeckManager() {
        // Mod event bus (lifecycle)
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModItems.register(modBus);
        ModMenuTypes.register(modBus);

        LOGGER.info("DeckManager loaded");

        // Forge event bus (game events)
        MinecraftForge.EVENT_BUS.register(this);

        if (FMLEnvironment.dist == Dist.CLIENT) {
//            // Client config (adjust if your config class uses a different field)
//            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ModClientConfig.CLIENT);
//
//            // Client-only event handlers
//            MinecraftForge.EVENT_BUS.register(ClientEvents.class);
//            MinecraftForge.EVENT_BUS.register(KeyInputHandler.class);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModNetworks.onCommonSetup(event);
    }
}
