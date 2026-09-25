package de.eron.redstoneplus.client;

import de.eron.redstoneplus.block.piston.SuperPistonBlock;
import de.eron.redstoneplus.menu.SuperPistonMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** Shows one slider: how many blocks the Super Piston arm reaches (1-13). */
public class SuperPistonScreen extends AbstractContainerScreen<SuperPistonMenu> {
    private static final int MAX = SuperPistonBlock.MAX_RANGE;
    private RangeSlider slider;
    private int shownRange = -1;

    public SuperPistonScreen(SuperPistonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 70;
    }

    @Override
    protected void init() {
        super.init();
        this.slider = new RangeSlider(this.leftPos + 10, this.topPos + 32, this.imageWidth - 20, 20, this.menu.range());
        this.addRenderableWidget(this.slider);
        this.shownRange = this.menu.range();
    }

    @Override
    protected void containerTick() {
        // the server value can arrive after the screen opened
        int range = this.menu.range();
        if (range != this.shownRange && !this.slider.isDragging()) {
            this.shownRange = range;
            this.slider.setRange(range);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xE0202A3A);
        graphics.renderOutline(this.leftPos, this.topPos, this.imageWidth, this.imageHeight, 0xFF5AA0FF);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 10, 8, 0xFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("gui.redstoneplus.push_limit", SuperPistonBlock.PUSH_LIMIT), 10, 19, 0xA0C8FF, false);
    }

    private class RangeSlider extends AbstractSliderButton {
        private boolean dragging;

        RangeSlider(int x, int y, int width, int height, int range) {
            super(x, y, width, height, Component.empty(), toValue(range));
            this.updateMessage();
        }

        boolean isDragging() {
            return this.dragging;
        }

        void setRange(int range) {
            this.value = toValue(range);
            this.updateMessage();
        }

        private int range() {
            return 1 + Mth.floor(this.value * (MAX - 1) + 0.5);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("gui.redstoneplus.range", this.range()));
        }

        @Override
        protected void applyValue() {
            int range = this.range();
            if (range != SuperPistonScreen.this.shownRange) {
                SuperPistonScreen.this.shownRange = range;
                SuperPistonScreen.this.minecraft.gameMode.handleInventoryButtonClick(SuperPistonScreen.this.menu.containerId, range);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            this.dragging = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            this.dragging = false;
            super.onRelease(mouseX, mouseY);
        }
    }

    private static double toValue(int range) {
        return (Mth.clamp(range, 1, MAX) - 1) / (double) (MAX - 1);
    }
}
