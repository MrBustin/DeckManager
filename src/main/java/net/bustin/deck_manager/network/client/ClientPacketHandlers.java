package net.bustin.deck_manager.network.client;

import net.bustin.deck_manager.network.SyncDeckPresetsS2CPacket;
import net.bustin.deck_manager.screen.CardDeckStationScreen;
import net.minecraft.client.Minecraft;

public class ClientPacketHandlers {
    public static void handleSyncDeckPresets(SyncDeckPresetsS2CPacket message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CardDeckStationScreen screen) {
            screen.setPresetSummaries(message.pos(), message.presets());
        }
    }

    private ClientPacketHandlers() {
    }
}
