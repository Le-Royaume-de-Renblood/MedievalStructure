package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.event.ClientModEvents;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class InnRoomsScreen extends Screen {
    private static final int TEXT = 0xFFE8E0CC;
    private static final int MUTED = 0xFFBBAF96;
    private static final int GOLD = 0xFFFFC86B;
    private static final int GREEN = 0xFF73D17C;
    private static final int RED = 0xFFFF6B6B;

    private Inn inn;
    private final Screen parent;
    private int imageWidth = 440;
    private int imageHeight = 286;
    private int left;
    private int top;
    private int page = 0;
    private final int itemsPerPage = 6;

    public InnRoomsScreen(Inn inn, Screen parent) {
        super(Component.literal("Chambres"));
        this.inn = inn;
        this.parent = parent;
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        imageWidth = Math.min(480, Math.max(380, width - 36));
        imageHeight = Math.min(320, Math.max(286, height - 32));
        left = (width - imageWidth) / 2;
        top = (height - imageHeight) / 2;
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        addRenderableWidget(new ModernInnButton(left + 18, top + imageHeight - 34, 86, 24, Component.translatable("gui.back"), b -> Minecraft.getInstance().setScreen(parent), ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(left + imageWidth - 82, top + imageHeight - 34, 28, 24, Component.literal("<"), b -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }, ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(left + imageWidth - 46, top + imageHeight - 34, 28, 24, Component.literal(">"), b -> {
            if (inn != null && (page + 1) * itemsPerPage < inn.getRooms().size()) {
                page++;
                rebuildWidgets();
            }
        }, ModernInnButton.Style.SECONDARY));

        if (inn == null) return;
        List<InnRoom> rooms = inn.getRooms();
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, rooms.size());
        for (int i = start; i < end; i++) {
            InnRoom room = rooms.get(i);
            int rowY = top + 74 + (i - start) * 30;
            addRenderableWidget(new ModernInnButton(left + imageWidth - 156, rowY - 5, 72, 22, Component.literal("Voir"), b -> {
                if (room.getP1() != null && room.getP2() != null) {
                    ClientModEvents.setRoomVisualization(room.getP1(), room.getP2(), 160);
                    onClose();
                }
            }, ModernInnButton.Style.PRIMARY));
            if (room.isBusy()) {
                addRenderableWidget(new ModernInnButton(left + imageWidth - 78, rowY - 5, 58, 22, Component.literal("Exclure"), b -> PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 5, room.getNumber())), ModernInnButton.Style.DANGER));
            }
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
        guiGraphics.drawString(this.font, "Chambres - " + inn.getName(), left + 18, top + 14, GOLD, false);
        guiGraphics.drawString(this.font, "Creation", left + 22, top + 48, MUTED, false);
        guiGraphics.drawString(this.font, "/ms auberge room create " + inn.getName() + " <numero> <taille> <prix>", left + 86, top + 48, TEXT, false);
        guiGraphics.drawString(this.font, "Selection au baton puis /ms auberge room confirm", left + 86, top + 61, MUTED, false);

        List<InnRoom> rooms = inn.getRooms();
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, rooms.size());
        for (int i = start; i < end; i++) {
            InnRoom room = rooms.get(i);
            int rowY = top + 74 + (i - start) * 30;
            guiGraphics.fill(left + 18, rowY - 8, left + imageWidth - 18, rowY + 18, 0x552B2B2B);
            int statusColor = room.isDirty() ? RED : room.isBusy() ? 0xFFFFB14A : GREEN;
            String status = room.isDirty() ? "sale" : room.isBusy() ? "occupee" : "libre";
            guiGraphics.drawString(this.font, "Ch. " + room.getNumber(), left + 26, rowY, GOLD, false);
            guiGraphics.drawString(this.font, status, left + 78, rowY, statusColor, false);
            guiGraphics.drawString(this.font, (int) room.getPrice() + " / jour", left + 148, rowY, TEXT, false);
            if (room.isBusy()) {
                guiGraphics.drawString(this.font, room.getOccupantName(), left + 220, rowY, MUTED, false);
            }
        }

        int pages = Math.max(1, (int) Math.ceil(rooms.size() / (double) itemsPerPage));
        guiGraphics.drawCenteredString(this.font, (page + 1) + " / " + pages, left + imageWidth - 118, top + imageHeight - 27, MUTED);
    }
}
