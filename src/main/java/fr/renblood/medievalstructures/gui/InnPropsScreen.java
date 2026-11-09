package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.event.ClientModEvents;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnProp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InnPropsScreen extends Screen {
    private static final int TEXT = 0xFFE8E0CC;
    private static final int MUTED = 0xFFBBAF96;
    private static final int GOLD = 0xFFFFC86B;
    private final Inn inn;
    private final Screen parent;
    private int imageWidth = 420;
    private int imageHeight = 260;
    private int left;
    private int top;
    private int page = 0;
    private final int itemsPerPage = 6;
    private final List<InnProp> dirtyProps;

    public InnPropsScreen(Inn inn, Screen parent) {
        super(Component.literal("Maintenance"));
        this.inn = inn;
        this.parent = parent;
        this.dirtyProps = inn.getProps().stream().filter(p -> !p.isActive()).collect(Collectors.toList());
    }

    @Override
    protected void init() {
        super.init();
        imageWidth = Math.min(460, Math.max(360, width - 36));
        imageHeight = Math.min(310, Math.max(260, height - 32));
        left = (width - imageWidth) / 2;
        top = (height - imageHeight) / 2;
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        addRenderableWidget(new ModernInnButton(left + 18, top + imageHeight - 34, 86, 24, Component.translatable("gui.back"), b -> Minecraft.getInstance().setScreen(parent), ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(left + 112, top + imageHeight - 34, 104, 24, Component.literal("Tout voir"), b -> {
            List<BlockPos> positions = new ArrayList<>();
            for (InnProp prop : dirtyProps) positions.add(prop.getPos());
            ClientModEvents.setHighlightedProps(positions, 600);
            onClose();
        }, ModernInnButton.Style.PRIMARY));
        addRenderableWidget(new ModernInnButton(left + imageWidth - 82, top + imageHeight - 34, 28, 24, Component.literal("<"), b -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }, ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(left + imageWidth - 46, top + imageHeight - 34, 28, 24, Component.literal(">"), b -> {
            if ((page + 1) * itemsPerPage < dirtyProps.size()) {
                page++;
                rebuildWidgets();
            }
        }, ModernInnButton.Style.SECONDARY));

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, dirtyProps.size());
        for (int i = start; i < end; i++) {
            InnProp prop = dirtyProps.get(i);
            int rowY = top + 66 + (i - start) * 30;
            addRenderableWidget(new ModernInnButton(left + imageWidth - 104, rowY - 5, 82, 22, Component.literal("Localiser"), b -> {
                ClientModEvents.setHighlightedProps(Collections.singletonList(prop.getPos()), 600);
                onClose();
            }, ModernInnButton.Style.PRIMARY));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        drawPanel(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderContent(guiGraphics);
    }

    private void drawPanel(GuiGraphics guiGraphics) {
        guiGraphics.fill(left - 2, top - 2, left + imageWidth + 2, top + imageHeight + 2, 0xAA000000);
        guiGraphics.fillGradient(left, top, left + imageWidth, top + imageHeight, 0xF01F1B17, 0xF0111517);
        guiGraphics.fill(left, top, left + imageWidth, top + 36, 0xFF2D241B);
        guiGraphics.fill(left, top + 36, left + imageWidth, top + 37, 0xFF6B4D25);
    }

    private void renderContent(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, "Maintenance - " + inn.getName(), left + 18, top + 14, GOLD, false);
        guiGraphics.drawString(this.font, dirtyProps.size() + " element(s) a nettoyer", left + 22, top + 48, MUTED, false);
        if (dirtyProps.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Tout est propre.", left + imageWidth / 2, top + imageHeight / 2, GOLD);
            return;
        }

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, dirtyProps.size());
        for (int i = start; i < end; i++) {
            InnProp prop = dirtyProps.get(i);
            int rowY = top + 66 + (i - start) * 30;
            guiGraphics.fill(left + 18, rowY - 8, left + imageWidth - 18, rowY + 18, 0x552B2B2B);
            guiGraphics.drawString(this.font, "ID " + prop.getId(), left + 26, rowY, GOLD, false);
            guiGraphics.drawString(this.font, prop.getType(), left + 78, rowY, TEXT, false);
            guiGraphics.drawString(this.font, "maintenance", left + 166, rowY, 0xFFFF6B6B, false);
        }

        int pages = Math.max(1, (int) Math.ceil(dirtyProps.size() / (double) itemsPerPage));
        guiGraphics.drawCenteredString(this.font, (page + 1) + " / " + pages, left + imageWidth - 118, top + imageHeight - 27, MUTED);
    }
}
