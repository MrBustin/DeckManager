package net.bustin.deck_manager.network;

import net.bustin.deck_manager.DeckManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetworks {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DeckManager.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(packetId++, RequestDeckPresetsC2SPacket.class,
                    RequestDeckPresetsC2SPacket::encode,
                    RequestDeckPresetsC2SPacket::decode,
                    RequestDeckPresetsC2SPacket::handle);
            CHANNEL.registerMessage(packetId++, SwapDeckCardsC2SPacket.class,
                    SwapDeckCardsC2SPacket::encode,
                    SwapDeckCardsC2SPacket::decode,
                    SwapDeckCardsC2SPacket::handle);
            CHANNEL.registerMessage(packetId++, SyncDeckPresetsS2CPacket.class,
                    SyncDeckPresetsS2CPacket::encode,
                    SyncDeckPresetsS2CPacket::decode,
                    SyncDeckPresetsS2CPacket::handle);
        });
    }

    private ModNetworks() {
    }
}
