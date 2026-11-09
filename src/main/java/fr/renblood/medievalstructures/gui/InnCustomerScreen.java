package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import fr.renblood.medievalstructures.client.economy.AdaptiveEconomyScreen;
import fr.renblood.medievalstructures.client.economy.EconomyUi;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class InnCustomerScreen extends AdaptiveEconomyScreen<InnCustomerMenu> {
    private static final int TEXT = 0xFFE8E0CC;
    private static final int MUTED = 0xFFBBAF96;
    private static final int GOLD = 0xFFFFC86B;
    private Inn inn;
    private final List<Button> roomButtons = new ArrayList<>();

    public InnCustomerScreen(InnCustomerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 420;
        this.imageHeight = 260;
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        this.imageWidth = Math.min(460, Math.max(360, this.width - 36));
        this.imageHeight = Math.min(310, Math.max(260, this.height - 32));
        super.init();
        rebuildRoomButtons();
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        rebuildRoomButtons();
    }

    private void rebuildRoomButtons() {
        roomButtons.forEach(this::removeWidget);
        roomButtons.clear();
        if (inn == null) return;

        int x = leftPos;
        int y = topPos;
        int row = 0;
        for (InnRoom room : inn.getRooms()) {
            if (row >= 7) break;
            if (!room.isBusy() && !room.isDirty()) {
                int rowY = y + 64 + row * 27;
                Button button = new ModernInnButton(x + imageWidth - 132, rowY - 4, 108, 22,
                        Component.literal("Louer"), b -> rentRoom(room.getNumber()), ModernInnButton.Style.SUCCESS);
                addRenderableWidget(button);
                roomButtons.add(button);
                row++;
            }
        }
    }

    private void rentRoom(int roomNumber) {
        if (inn != null) PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 1, roomNumber));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xAA000000);
        guiGraphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xF01F1B17, 0xF0111517);
        guiGraphics.fill(x, y, x + imageWidth, y + 36, 0xFF2D241B);
        guiGraphics.fill(x, y + 36, x + imageWidth, y + 37, 0xFF6B4D25);
    }

    @Override
    public void renderLogical(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.renderLogical(guiGraphics, mouseX, mouseY, partialTick);
        renderContent(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderContent(GuiGraphics guiGraphics) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(this.font, inn != null ? "Bienvenue - " + inn.getName() : "Auberge", x + 18, y + 14, GOLD, false);
        if (inn == null) {
            guiGraphics.drawString(this.font, "Chargement...", x + 18, y + 60, MUTED, false);
            return;
        }

        guiGraphics.drawString(this.font, "Chambres disponibles", x + 22, y + 48, MUTED, false);
        int row = 0;
        for (InnRoom room : inn.getRooms()) {
            if (row >= 7) break;
            if (!room.isBusy() && !room.isDirty()) {
                int rowY = y + 64 + row * 27;
                guiGraphics.fill(x + 18, rowY - 7, x + imageWidth - 18, rowY + 18, 0x552B2B2B);
                guiGraphics.drawString(this.font, "Chambre " + room.getNumber(), x + 26, rowY, TEXT, false);
                guiGraphics.drawString(this.font, "Taille " + room.getSize() + " | "
                        + MedievalCoinsIntegration.formatMoney((long) room.getPrice()) + " / jour", x + 112, rowY, GOLD, false);
                row++;
            }
        }
        if (row == 0) {
            guiGraphics.drawString(this.font, "Aucune chambre libre pour le moment.", x + 22, y + 72, MUTED, false);
        }
    }
    @Override protected String economyResource(){return inn==null?"transaction":"inn:"+inn.getEconomyId();}
}
