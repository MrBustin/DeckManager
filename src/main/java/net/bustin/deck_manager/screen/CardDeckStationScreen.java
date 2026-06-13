package net.bustin.deck_manager.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import iskallia.vault.client.gui.framework.ScreenRenderers;
import iskallia.vault.client.gui.framework.ScreenTextures;
import net.bustin.deck_manager.menu.CardDeckStationMenu;
import net.bustin.deck_manager.network.LoadDeckPresetC2SPacket;
import net.bustin.deck_manager.network.ModNetworks;
import net.bustin.deck_manager.network.RequestDeckPresetsC2SPacket;
import net.bustin.deck_manager.network.SaveHeldDeckPresetC2SPacket;
import net.bustin.deck_manager.network.SyncDeckPresetsS2CPacket.PreviewCard;
import net.bustin.deck_manager.network.SyncDeckPresetsS2CPacket.PresetSummary;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CardDeckStationScreen extends AbstractContainerScreen<CardDeckStationMenu> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");
    private static final ResourceLocation SOPHISTICATED_GUI_CONTROLS =
            new ResourceLocation("sophisticatedcore", "textures/gui/gui_controls.png");
    private static final int DECK_BACKGROUND_PADDING = 20;
    private static final int DECK_SLOT_ORIGIN = 21;
    private static final int PREVIEW_DECK_X = 168;
    private static final int PREVIEW_DECK_Y = 52;

    private final List<PresetSummary> presets = new ArrayList<>();
    private EditBox presetNameBox;
    private Button loadButton;
    private int selectedPreset = -1;
    private int presetSyncTicks = 0;

    public CardDeckStationScreen(CardDeckStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 370;
        this.imageHeight = 270;
    }

    @Override
    protected void init() {
        super.init();

        this.presetNameBox = new EditBox(this.font, this.leftPos + 12, this.topPos + 28, 126, 18,
                new TextComponent("Preset Name"));
        this.presetNameBox.setMaxLength(32);
        this.presetNameBox.setValue("");
        this.addRenderableWidget(this.presetNameBox);

        this.addRenderableWidget(new Button(this.leftPos + 144, this.topPos + 27, 42, 20,
                new TextComponent("Save"), button -> ModNetworks.CHANNEL.sendToServer(
                new SaveHeldDeckPresetC2SPacket(this.menu.getBlockPos(), this.presetNameBox.getValue()))));

        this.addRenderableWidget(new Button(this.leftPos + 190, this.topPos + 27, 46, 20,
                new TextComponent("Refresh"), button -> requestPresetSync()));

        this.addRenderableWidget(new Button(this.leftPos + 14, this.topPos + 119, 44, 20,
                new TextComponent("Prev"), button -> selectPreset(this.selectedPreset - 1)));

        this.addRenderableWidget(new Button(this.leftPos + 62, this.topPos + 119, 44, 20,
                new TextComponent("Next"), button -> selectPreset(this.selectedPreset + 1)));

        this.loadButton = new Button(this.leftPos + 110, this.topPos + 119, 42, 20,
                new TextComponent("Load"), button -> ModNetworks.CHANNEL.sendToServer(
                new LoadDeckPresetC2SPacket(this.menu.getBlockPos(),
                        getSelectedPreset().map(PresetSummary::name).orElse(""))));
        this.addRenderableWidget(this.loadButton);

        requestPresetSync();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.presetNameBox.tick();
        updateLoadButtonState();
        if (++this.presetSyncTicks >= 20) {
            this.presetSyncTicks = 0;
            requestPresetSync();
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        int x = this.leftPos;
        int y = this.topPos;
        fill(poseStack, x, y, x + this.imageWidth, y + this.imageHeight, 0xFF1F2329);
        fill(poseStack, x + 4, y + 4, x + this.imageWidth - 4, y + this.imageHeight - 4, 0xFF2F3540);
        fill(poseStack, x + 12, y + 52, x + 156, y + 116, 0xFF171A1F);
        fill(poseStack, x + 168, y + 52, x + this.imageWidth - 12, y + 166, 0xFF171A1F);
        fill(poseStack, x + 168, y + 186, x + this.imageWidth - 12, y + 264, 0xFF171A1F);

        drawSlot(poseStack, x + 144, y + 53);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(poseStack, x + 169 + column * 18, y + 180 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(poseStack, x + 169 + column * 18, y + 238);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        this.font.draw(poseStack, this.title, 12, 12, 0xE6E6E6);
        this.font.draw(poseStack, new TextComponent("Preset name"), 12, 18, 0xAAB4C3);
        this.font.draw(poseStack, new TextComponent("Saved presets"), 14, 56, 0xBFC7D5);
        this.font.draw(poseStack, new TextComponent("Deck"), 144, 44, 0x8F98A8);
        this.font.draw(poseStack, new TextComponent("Selected preset"), 170, 42, 0xBFC7D5);
        this.font.draw(poseStack, new TextComponent("Inventory"), 170, 170, 0xBFC7D5);

        renderPresetList(poseStack);
        renderSelectedPreset(poseStack);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderSelectedPresetCards(poseStack);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= this.leftPos + 14 && mouseX <= this.leftPos + 108
                && mouseY >= this.topPos + 72 && mouseY <= this.topPos + 112) {
            int start = Math.max(0, Math.min(this.selectedPreset - 2, this.presets.size() - 5));
            int clicked = start + ((int) mouseY - (this.topPos + 72)) / 11;
            if (clicked >= 0 && clicked < this.presets.size()) {
                selectPreset(clicked);
                return true;
            }
        }

        if (isPresetPreviewGrid(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void setPresetSummaries(BlockPos pos, List<PresetSummary> syncedPresets) {
        if (!this.menu.getBlockPos().equals(pos)) {
            return;
        }

        String selectedName = getSelectedPreset().map(PresetSummary::name).orElse("");
        this.presets.clear();
        this.presets.addAll(syncedPresets);
        this.selectedPreset = findPresetIndex(selectedName);
        if (this.selectedPreset < 0 && !this.presets.isEmpty()) {
            this.selectedPreset = 0;
        }
        updateLoadButtonState();
    }

    private void renderPresetList(PoseStack poseStack) {
        if (this.presets.isEmpty()) {
            this.font.draw(poseStack, new TextComponent("No presets"), 18, 76, 0x8F98A8);
            return;
        }

        int start = Math.max(0, Math.min(this.selectedPreset - 2, this.presets.size() - 5));
        for (int i = 0; i < 4 && start + i < this.presets.size(); i++) {
            int presetIndex = start + i;
            int y = 74 + i * 11;
            PresetSummary preset = this.presets.get(presetIndex);
            int color = presetIndex == this.selectedPreset ? 0xFFFFFF : presetListColor(preset);
            String name = this.font.plainSubstrByWidth(preset.name(), 86);
            this.font.draw(poseStack, new TextComponent(name), 18, y, color);
        }
    }

    private void renderSelectedPreset(PoseStack poseStack) {
        PresetSummary preset = getSelectedPreset().orElse(null);
        if (preset == null) {
            this.font.draw(poseStack, new TextComponent("Put deck in slot,"), 18, 126, 0x8F98A8);
            this.font.draw(poseStack, new TextComponent("enter a name,"), 18, 138, 0x8F98A8);
            this.font.draw(poseStack, new TextComponent("then Save."), 18, 150, 0x8F98A8);
            return;
        }

        int cardColor = preset.missingCards() > 0 ? 0xF0A55B : 0x9EDB8F;
        this.font.draw(poseStack, new TextComponent("Deck: " + trimForPreview(preset.sourceDeckId())), 18, 126, 0xAAB4C3);
        this.font.draw(poseStack, new TextComponent("Cards: " + preset.availableCards() + "/" + preset.cardCount()),
                18, 138, cardColor);
        this.font.draw(poseStack, new TextComponent("Return: " + preset.currentDeckCards()), 18, 150, 0xAAB4C3);
        this.font.draw(poseStack, new TextComponent(loadStatusText(preset)), 18, 162, loadStatusColor(preset));
        this.font.draw(poseStack, new TextComponent("Saved: " + DATE_FORMAT.format(new Date(preset.createdAt()))),
                18, 174, 0xAAB4C3);
    }

    private void selectPreset(int index) {
        if (this.presets.isEmpty()) {
            this.selectedPreset = -1;
            return;
        }

        if (index < 0) {
            index = this.presets.size() - 1;
        } else if (index >= this.presets.size()) {
            index = 0;
        }

        this.selectedPreset = index;
        updateLoadButtonState();
    }

    private java.util.Optional<PresetSummary> getSelectedPreset() {
        if (this.selectedPreset < 0 || this.selectedPreset >= this.presets.size()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(this.presets.get(this.selectedPreset));
    }

    private int findPresetIndex(String name) {
        for (int i = 0; i < this.presets.size(); i++) {
            if (this.presets.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private void requestPresetSync() {
        ModNetworks.CHANNEL.sendToServer(new RequestDeckPresetsC2SPacket(this.menu.getBlockPos()));
    }

    private void updateLoadButtonState() {
        if (this.loadButton != null) {
            this.loadButton.active = getSelectedPreset().map(PresetSummary::canLoad).orElse(false);
        }
    }

    private String trimForPreview(String value) {
        return this.font.plainSubstrByWidth(value == null || value.isEmpty() ? "-" : value, 108);
    }

    private void renderSelectedPresetCards(PoseStack poseStack) {
        clearPresetPreviewGrid(poseStack);

        PresetSummary preset = getSelectedPreset().orElse(null);
        if (preset == null) {
            return;
        }

        renderDeckLayout(poseStack, preset);

        List<PreviewCard> previewCards = preset.previewCards();
        for (int i = 0; i < previewCards.size() && i < 36; i++) {
            PreviewCard previewCard = previewCards.get(i);
            int x = deckPreviewX(preset) + previewCard.x() * 18;
            int y = deckPreviewY(preset) + previewCard.y() * 18;
            ItemStack stack = previewCard.stack();
            this.itemRenderer.renderAndDecorateItem(stack, x, y);
            if (!isPreviewCardAvailable(preset, i)) {
                renderSophisticatedFilterOverlay(poseStack, x, y);
            }
        }
    }

    private boolean isPreviewCardAvailable(PresetSummary preset, int previewIndex) {
        return previewIndex < preset.previewAvailable().size() && preset.previewAvailable().get(previewIndex);
    }

    private void renderSophisticatedFilterOverlay(PoseStack poseStack, int x, int y) {
        poseStack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SOPHISTICATED_GUI_CONTROLS);
        blit(poseStack, x, y, 77, 0, 16, 16);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private boolean isPresetPreviewGrid(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + PREVIEW_DECK_X && mouseX < this.leftPos + PREVIEW_DECK_X + 182
                && mouseY >= this.topPos + PREVIEW_DECK_Y && mouseY < this.topPos + PREVIEW_DECK_Y + 110;
    }

    private void clearPresetPreviewGrid(PoseStack poseStack) {
        int x = this.leftPos;
        int y = this.topPos;
        fill(poseStack, x + 168, y + 52, x + this.imageWidth - 12, y + 166, 0xFF171A1F);
    }

    private void renderDeckLayout(PoseStack poseStack, PresetSummary preset) {
        List<String> layoutRows = preset.layoutRows();
        if (layoutRows.isEmpty()) {
            drawVaultDeckBackground(poseStack, this.leftPos + PREVIEW_DECK_X, this.topPos + PREVIEW_DECK_Y,
                    DECK_BACKGROUND_PADDING + 9 * 18, DECK_BACKGROUND_PADDING + 4 * 18);
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 9; column++) {
                    drawCardDeckSlot(poseStack, this.leftPos + PREVIEW_DECK_X + DECK_BACKGROUND_PADDING + column * 18,
                            this.topPos + PREVIEW_DECK_Y + DECK_BACKGROUND_PADDING + row * 18);
                }
            }
            return;
        }

        int deckX = this.leftPos + PREVIEW_DECK_X;
        int deckY = this.topPos + PREVIEW_DECK_Y;
        drawVaultDeckBackground(poseStack, deckX, deckY, deckBackgroundWidth(layoutRows), deckBackgroundHeight(layoutRows));

        int startX = deckPreviewX(preset) - 1;
        int startY = deckPreviewY(preset) - 1;
        for (int row = 0; row < layoutRows.size(); row++) {
            String layoutRow = layoutRows.get(row);
            for (int column = 0; column < layoutRow.length(); column++) {
                char slotType = layoutRow.charAt(column);
                if (slotType == 'X') {
                    continue;
                }
                drawCardDeckSlot(poseStack, startX + column * 18, startY + row * 18);
                if (slotType == 'A') {
                    fill(poseStack, startX + column * 18 + 2, startY + row * 18 + 2,
                            startX + column * 18 + 16, startY + row * 18 + 16, 0x552D73D5);
                }
            }
        }
    }

    private int deckPreviewX(PresetSummary preset) {
        return this.leftPos + PREVIEW_DECK_X + DECK_SLOT_ORIGIN;
    }

    private int deckPreviewY(PresetSummary preset) {
        return this.topPos + PREVIEW_DECK_Y + DECK_SLOT_ORIGIN;
    }

    private int deckBackgroundWidth(List<String> layoutRows) {
        return DECK_BACKGROUND_PADDING + layoutWidth(layoutRows) * 18;
    }

    private int deckBackgroundHeight(List<String> layoutRows) {
        return DECK_BACKGROUND_PADDING + layoutRows.size() * 18;
    }

    private int layoutWidth(List<String> layoutRows) {
        int width = 0;
        for (String row : layoutRows) {
            width = Math.max(width, row.length());
        }
        return width == 0 ? 9 : width;
    }


    private int presetListColor(PresetSummary preset) {
        if (preset.canLoad()) {
            return 0x9EDB8F;
        }
        if (preset.missingCards() > 0) {
            return 0xF0A55B;
        }
        return 0x8F98A8;
    }

    private String loadStatusText(PresetSummary preset) {
        if (!preset.hasDeck()) {
            return "Needs deck";
        }
        if (!preset.compatibleDeck()) {
            return "Wrong deck type";
        }
        if (preset.missingCards() > 0) {
            return "Missing: " + preset.missingCards();
        }
        if (!preset.canStoreReturnedCards()) {
            return "Storage full";
        }
        return "Ready";
    }

    private int loadStatusColor(PresetSummary preset) {
        if (preset.canLoad()) {
            return 0x9EDB8F;
        }
        if (preset.missingCards() > 0 || !preset.canStoreReturnedCards()) {
            return 0xF0A55B;
        }
        return 0xC96C6C;
    }

    private void drawSlot(PoseStack poseStack, int x, int y) {
        fill(poseStack, x, y, x + 18, y + 18, 0xFF0F1115);
        fill(poseStack, x + 1, y + 1, x + 17, y + 17, 0xFF252A33);
    }

    private void drawCardDeckSlot(PoseStack poseStack, int x, int y) {
        ScreenTextures.INSET_CARD_SLOT_BACKGROUND.blit(poseStack, x, y, 0, 18, 18);
    }

    private void drawVaultDeckBackground(PoseStack poseStack, int x, int y, int width, int height) {
        ScreenRenderers.getImmediate().render(ScreenTextures.CARD_DECK_BACKGROUND_9, poseStack, x, y, 0, width, height);
    }
}
