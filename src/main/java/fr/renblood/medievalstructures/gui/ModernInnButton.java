package fr.renblood.medievalstructures.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ModernInnButton extends Button {
    public static final int GOLD = 0xFFFFC86B;

    public enum Style {
        PRIMARY(0xFF7B4E22, 0xFFC98536),
        SECONDARY(0xFF343434, 0xFF7B6B55),
        SUCCESS(0xFF2E6237, 0xFF74AA61),
        DANGER(0xFF6D2929, 0xFFB24A3E);

        private final int base;
        private final int border;

        Style(int base, int border) {
            this.base = base;
            this.border = border;
        }
    }

    private final Style style;

    public ModernInnButton(int x, int y, int width, int height, Component message, OnPress onPress, Style style) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int bg = this.active ? style.base : 0xFF242424;
        int border = this.isHoveredOrFocused() && this.active ? GOLD : style.border;
        guiGraphics.fill(x, y, x + this.width, y + this.height, 0xBB000000);
        guiGraphics.fill(x + 1, y + 1, x + this.width - 1, y + this.height - 1, bg);
        guiGraphics.fill(x, y, x + this.width, y + 1, border);
        guiGraphics.fill(x, y + this.height - 1, x + this.width, y + this.height, 0xFF17120E);
        guiGraphics.fill(x, y, x + 1, y + this.height, border);
        guiGraphics.fill(x + this.width - 1, y, x + this.width, y + this.height, 0xFF17120E);
        int color = this.active ? 0xFFFFFFFF : 0xFF8E8E8E;
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(), x + width / 2, y + (height - 8) / 2, color);
    }
}
