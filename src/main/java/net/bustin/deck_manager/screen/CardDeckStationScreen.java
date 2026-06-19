package net.bustin.deck_manager.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import iskallia.vault.client.gui.framework.ScreenRenderers;
import iskallia.vault.client.gui.framework.ScreenTextures;
import net.bustin.deck_manager.blocks.entity.custom.CardDeckStationBlockEntity;
import net.bustin.deck_manager.menu.CardDeckStationMenu;
import net.bustin.deck_manager.network.ModNetworks;
import net.bustin.deck_manager.network.RequestDeckPresetsC2SPacket;
import net.bustin.deck_manager.network.SwapDeckCardsC2SPacket;
import net.bustin.deck_manager.network.SyncDeckPresetsS2CPacket.PreviewCard;
import net.bustin.deck_manager.network.SyncDeckPresetsS2CPacket.PresetSummary;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CardDeckStationScreen extends AbstractContainerScreen<CardDeckStationMenu> {
    private static final ResourceLocation DECK_MANAGER_GUI =
            new ResourceLocation("deck_manager", "textures/gui/deck_manager_gui2.png");
    private static final ResourceLocation SAVE_BUTTON =
            new ResourceLocation("deck_manager", "textures/gui/button_save.png");
    private static final ResourceLocation SWAP_BUTTON =
            new ResourceLocation("deck_manager", "textures/gui/button_swap.png");
    private static final ResourceLocation SOPHISTICATED_GUI_CONTROLS =
            new ResourceLocation("sophisticatedcore", "textures/gui/gui_controls.png");
    private static final int GUI_TEXTURE_WIDTH = 231;
    private static final int GUI_TEXTURE_HEIGHT = 291;
    private static final int DECK_TITLE_HEIGHT = 13;
    private static final int DECK_TITLE_HORIZONTAL_PADDING = 8;
    private static final int DECK_TITLE_EXTRA_WIDTH = 14;
    private static final int DECK_TITLE_Y_OFFSET = 5;
    private static final int DECK_BACKGROUND_PADDING = 20;
    private static final int DECK_SLOT_ORIGIN = 21;
    private static final int RIGHT_PANEL_X = 25;
    private static final int RIGHT_PANEL_WIDTH = 197;
    private static final int PREVIEW_MIN_Y = 16;
    private static final int PREVIEW_PANEL_BOTTOM = 167;
    private static final int ACTION_BUTTON_Y = 178;
    private static final int ACTION_BUTTON_X = 115;
    private static final int ACTION_BUTTON_WIDTH = 20;
    private static final int ACTION_BUTTON_HEIGHT = 18;
    private static final int SAVE_BUTTON_TEXTURE_WIDTH = 30;
    private static final int SAVE_BUTTON_TEXTURE_HEIGHT = 28;
    private static final int SWAP_BUTTON_TEXTURE_WIDTH = 18;
    private static final int SWAP_BUTTON_TEXTURE_HEIGHT = 18;
    private static final int INVENTORY_LABEL_X = 43;
    private static final int INVENTORY_LABEL_Y = 196;

    private final List<PresetSummary> presets = new ArrayList<>();
    private Button swapButton;
    private int selectedPreset = -1;
    private int presetSyncTicks = 0;

    public CardDeckStationScreen(CardDeckStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 231;
        this.imageHeight = 291;
    }

    @Override
    protected void init() {
        super.init();

        this.swapButton = new Button(this.leftPos + ACTION_BUTTON_X, this.topPos + ACTION_BUTTON_Y,
                ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT, swapButtonText(), button -> ModNetworks.CHANNEL.sendToServer(
                new SwapDeckCardsC2SPacket(this.menu.getBlockPos()))) {
            @Override
            public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
                ResourceLocation texture = hasStoredDeck() ? SWAP_BUTTON : SAVE_BUTTON;
                int textureWidth = hasStoredDeck() ? SWAP_BUTTON_TEXTURE_WIDTH : SAVE_BUTTON_TEXTURE_WIDTH;
                int textureHeight = hasStoredDeck() ? SWAP_BUTTON_TEXTURE_HEIGHT : SAVE_BUTTON_TEXTURE_HEIGHT;

                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, this.active ? 1.0f : 0.45f);
                blit(poseStack, this.x, this.y, this.width, this.height, 0.0f, 0.0f,
                        textureWidth, textureHeight, textureWidth, textureHeight);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        };
        this.addRenderableWidget(this.swapButton);

        requestPresetSync();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateSwapButtonState();
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
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, DECK_MANAGER_GUI);
        blit(poseStack, x, y, 0, 0, this.imageWidth, this.imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        this.font.draw(poseStack, this.playerInventoryTitle, INVENTORY_LABEL_X, INVENTORY_LABEL_Y, 0x404040);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderSelectedPresetCards(poseStack);
        this.renderTooltip(poseStack, mouseX, mouseY);
        renderSwapButtonTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (isPresetPreviewGrid(mouseX, mouseY)) {
            return true;
        }

        return false;
    }

    public void setPresetSummaries(BlockPos pos, List<PresetSummary> syncedPresets) {
        if (!this.menu.getBlockPos().equals(pos)) {
            return;
        }

        String selectedName = getSelectedPreset().map(PresetSummary::name).orElse("");
        this.presets.clear();
        this.presets.addAll(syncedPresets);
        this.selectedPreset = this.presets.isEmpty() ? -1 : Math.max(0, findPresetIndex(selectedName));
        updateSwapButtonState();
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

    private void updateSwapButtonState() {
        if (this.swapButton != null) {
            this.swapButton.active = !this.menu.slots.get(CardDeckStationBlockEntity.DECK_SLOT).getItem().isEmpty();
            this.swapButton.setMessage(swapButtonText());
        }
    }

    private Component swapButtonText() {
        return new TextComponent(hasStoredDeck() ? "Swap" : "Save");
    }

    private boolean hasStoredDeck() {
        return !this.presets.isEmpty();
    }

    private void renderSwapButtonTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        if (this.swapButton != null && isSwapButtonHovered(mouseX, mouseY)) {
            this.renderTooltip(poseStack, swapButtonText(), mouseX, mouseY);
        }
    }

    private boolean isSwapButtonHovered(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + ACTION_BUTTON_X
                && mouseX < this.leftPos + ACTION_BUTTON_X + ACTION_BUTTON_WIDTH
                && mouseY >= this.topPos + ACTION_BUTTON_Y
                && mouseY < this.topPos + ACTION_BUTTON_Y + ACTION_BUTTON_HEIGHT;
    }

    private void renderSelectedPresetCards(PoseStack poseStack) {
        clearPresetPreviewGrid(poseStack);

        PresetSummary preset = getSelectedPreset().orElse(null);
        if (preset == null) {
            return;
        }

        renderDeckLayout(poseStack, preset);

        List<PreviewCard> previewCards = preset.previewCards();
        LayoutBounds layoutBounds = presetLayoutBounds(preset);
        for (int i = 0; i < previewCards.size() && i < 36; i++) {
            PreviewCard previewCard = previewCards.get(i);
            int x = deckPreviewX(preset) + (previewCard.x() - layoutBounds.minColumn()) * 18;
            int y = deckPreviewY(preset) + (previewCard.y() - layoutBounds.minRow()) * 18;
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
        PresetSummary preset = getSelectedPreset().orElse(null);
        if (preset == null) {
            return mouseX >= this.leftPos + RIGHT_PANEL_X && mouseX < this.leftPos + RIGHT_PANEL_X + RIGHT_PANEL_WIDTH
                    && mouseY >= this.topPos + PREVIEW_MIN_Y
                    && mouseY < this.topPos + PREVIEW_PANEL_BOTTOM;
        }

        LayoutBounds layoutBounds = presetLayoutBounds(preset);
        int deckX = deckBackgroundX(layoutBounds);
        int deckY = deckBackgroundY(layoutBounds);
        return mouseX >= deckX && mouseX < deckX + deckBackgroundWidth(layoutBounds)
                && mouseY >= deckY && mouseY < deckY + deckBackgroundHeight(layoutBounds);
    }

    private void clearPresetPreviewGrid(PoseStack poseStack) {
    }

    private void renderDeckLayout(PoseStack poseStack, PresetSummary preset) {
        List<String> layoutRows = preset.layoutRows();
        if (layoutRows.isEmpty()) {
            renderPositionFallbackDeckLayout(poseStack, preset);
            return;
        }

        LayoutBounds layoutBounds = layoutBounds(layoutRows);
        int deckX = deckBackgroundX(layoutBounds);
        int deckY = deckBackgroundY(layoutBounds);
        drawVaultDeckBackground(poseStack, deckX, deckY, deckBackgroundWidth(layoutBounds), deckBackgroundHeight(layoutBounds));
        drawDeckTitle(poseStack, preset, deckX, deckY, deckBackgroundWidth(layoutBounds));

        int startX = deckPreviewX(preset) - 1;
        int startY = deckPreviewY(preset) - 1;
        for (int row = layoutBounds.minRow(); row <= layoutBounds.maxRow(); row++) {
            String layoutRow = layoutRows.get(row);
            for (int column = layoutBounds.minColumn(); column <= layoutBounds.maxColumn() && column < layoutRow.length(); column++) {
                char slotType = Character.toUpperCase(layoutRow.charAt(column));
                if (!isDeckSlot(slotType)) {
                    continue;
                }
                int slotX = startX + (column - layoutBounds.minColumn()) * 18;
                int slotY = startY + (row - layoutBounds.minRow()) * 18;
                drawCardDeckSlot(poseStack, slotX, slotY);
                if (slotType == 'A') {
                    fill(poseStack, slotX + 2, slotY + 2, slotX + 16, slotY + 16, 0x552D73D5);
                }
            }
        }
    }

    private int deckPreviewX(PresetSummary preset) {
        LayoutBounds layoutBounds = presetLayoutBounds(preset);
        return deckBackgroundX(layoutBounds) + DECK_SLOT_ORIGIN;
    }

    private int deckPreviewY(PresetSummary preset) {
        LayoutBounds layoutBounds = presetLayoutBounds(preset);
        return deckBackgroundY(layoutBounds) + DECK_SLOT_ORIGIN;
    }

    private int deckBackgroundX(LayoutBounds layoutBounds) {
        int rightPanelCenter = this.leftPos + RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2;
        return rightPanelCenter - deckBackgroundWidth(layoutBounds) / 2;
    }

    private int deckBackgroundY(LayoutBounds layoutBounds) {
        int backgroundHeight = deckBackgroundHeight(layoutBounds);
        int centeredY = PREVIEW_MIN_Y + (PREVIEW_PANEL_BOTTOM - PREVIEW_MIN_Y - backgroundHeight) / 2;
        int maximumY = PREVIEW_PANEL_BOTTOM - backgroundHeight;
        return this.topPos + Math.max(PREVIEW_MIN_Y, Math.min(centeredY, maximumY));
    }

    private int deckBackgroundWidth(LayoutBounds layoutBounds) {
        return DECK_BACKGROUND_PADDING + (layoutBounds.width() + 1) * 18;
    }

    private int deckBackgroundHeight(LayoutBounds layoutBounds) {
        return DECK_BACKGROUND_PADDING + (layoutBounds.height() + 1) * 18;
    }

    private LayoutBounds layoutBounds(List<String> layoutRows) {
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        int minColumn = Integer.MAX_VALUE;
        int maxColumn = Integer.MIN_VALUE;

        for (int row = 0; row < layoutRows.size(); row++) {
            String layoutRow = layoutRows.get(row);
            for (int column = 0; column < layoutRow.length(); column++) {
                if (!isDeckSlot(layoutRow.charAt(column))) {
                    continue;
                }

                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }
        }

        if (minRow == Integer.MAX_VALUE) {
            return new LayoutBounds(0, 3, 0, 8);
        }

        return new LayoutBounds(minRow, maxRow, minColumn, maxColumn);
    }

    private boolean isDeckSlot(char slotType) {
        char normalizedSlotType = Character.toUpperCase(slotType);
        return normalizedSlotType == 'O' || normalizedSlotType == 'A';
    }

    private LayoutBounds presetLayoutBounds(PresetSummary preset) {
        return preset.layoutRows().isEmpty() ? previewCardBounds(preset.previewCards()) : layoutBounds(preset.layoutRows());
    }

    private void renderPositionFallbackDeckLayout(PoseStack poseStack, PresetSummary preset) {
        LayoutBounds layoutBounds = previewCardBounds(preset.previewCards());
        int deckX = deckBackgroundX(layoutBounds);
        int deckY = deckBackgroundY(layoutBounds);
        drawVaultDeckBackground(poseStack, deckX, deckY, deckBackgroundWidth(layoutBounds), deckBackgroundHeight(layoutBounds));
        drawDeckTitle(poseStack, preset, deckX, deckY, deckBackgroundWidth(layoutBounds));

        for (PreviewCard previewCard : preset.previewCards()) {
            drawCardDeckSlot(poseStack,
                    deckPreviewX(preset) + (previewCard.x() - layoutBounds.minColumn()) * 18 - 1,
                    deckPreviewY(preset) + (previewCard.y() - layoutBounds.minRow()) * 18 - 1);
        }
    }

    private LayoutBounds previewCardBounds(List<PreviewCard> previewCards) {
        if (previewCards.isEmpty()) {
            return new LayoutBounds(0, 3, 0, 8);
        }

        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        int minColumn = Integer.MAX_VALUE;
        int maxColumn = Integer.MIN_VALUE;

        for (PreviewCard previewCard : previewCards) {
            minRow = Math.min(minRow, previewCard.y());
            maxRow = Math.max(maxRow, previewCard.y());
            minColumn = Math.min(minColumn, previewCard.x());
            maxColumn = Math.max(maxColumn, previewCard.x());
        }

        return new LayoutBounds(minRow, maxRow, minColumn, maxColumn);
    }

    private record LayoutBounds(int minRow, int maxRow, int minColumn, int maxColumn) {
        int width() {
            return maxColumn - minColumn + 1;
        }

        int height() {
            return maxRow - minRow + 1;
        }
    }

    private void drawCardDeckSlot(PoseStack poseStack, int x, int y) {
        ScreenTextures.INSET_CARD_SLOT_BACKGROUND.blit(poseStack, x, y, 0, 18, 18);
    }

    private void drawDeckTitle(PoseStack poseStack, PresetSummary preset, int deckX, int deckY, int deckWidth) {
        String title = deckTitle(preset);
        int maxTextWidth = Math.max(0, deckWidth + DECK_TITLE_EXTRA_WIDTH - DECK_TITLE_HORIZONTAL_PADDING * 2);
        String trimmedTitle = this.font.plainSubstrByWidth(title, maxTextWidth);
        int textWidth = this.font.width(trimmedTitle);
        int titleWidth = textWidth + DECK_TITLE_HORIZONTAL_PADDING * 2 + DECK_TITLE_EXTRA_WIDTH;
        int titleX = deckX + (deckWidth - titleWidth) / 2;
        int titleY = deckY + DECK_TITLE_Y_OFFSET;
        int textY = titleY + (DECK_TITLE_HEIGHT - this.font.lineHeight) / 2;

        ScreenTextures.CARD_DECK_TITLE_LARGE_3.blit(poseStack, titleX, titleY, 0, titleWidth, DECK_TITLE_HEIGHT);
        this.font.draw(poseStack, new TextComponent(trimmedTitle),
                titleX + DECK_TITLE_HORIZONTAL_PADDING + DECK_TITLE_EXTRA_WIDTH / 2, textY, 0x404040);
    }

    private String deckTitle(PresetSummary preset) {
        String title;
        if (preset.sourceDeckName() != null && !preset.sourceDeckName().isBlank()) {
            title = preset.sourceDeckName();
        } else if (preset.sourceDeckId() != null && !preset.sourceDeckId().isBlank()) {
            title = preset.sourceDeckId();
        } else {
            title = "Deck";
        }
        return title.replaceFirst("\\s*\\([^)]*\\)\\s*$", "");
    }

    private void drawVaultDeckBackground(PoseStack poseStack, int x, int y, int width, int height) {
        ScreenRenderers.getImmediate().render(ScreenTextures.CARD_DECK_BACKGROUND_9, poseStack, x, y, 0, width, height);
    }
}
