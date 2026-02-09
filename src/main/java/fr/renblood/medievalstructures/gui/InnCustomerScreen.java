package fr.renblood.medievalstructures.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class InnCustomerScreen extends AbstractContainerScreen<InnCustomerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MedievalStructures.MODID, "textures/gui/inn_customer_gui.png");
    private Inn inn;
    private final List<Button> roomButtons = new ArrayList<>();

    public InnCustomerScreen(InnCustomerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 226;
        this.imageHeight = 166;
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        // Les boutons seront ajoutés quand on recevra les données de l'auberge
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        rebuildRoomButtons();
    }

    private void rebuildRoomButtons() {
        roomButtons.forEach(this::removeWidget);
        roomButtons.clear();

        if (inn == null) return;

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int i = 0;

        for (InnRoom room : inn.getRooms()) {
            if (i > 5) break; // Limite d'affichage

            if (!room.isBusy() && !room.isDirty()) {
                Button btn = Button.builder(Component.translatable("gui.medieval_structures.rent_room", room.getNumber(), room.getPrice()), 
                        b -> rentRoom(room.getNumber()))
                        .bounds(x + 8, y + 30 + (i * 22), 200, 20)
                        .build();
                addRenderableWidget(btn);
                roomButtons.add(btn);
                i++;
            }
        }
    }

    private void rentRoom(int roomNumber) {
        if (inn != null) {
            // Action 1 = Rent Room
            PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 1, roomNumber));
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        // Utilisation de blit avec les dimensions exactes de l'image (226x166)
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 226, 166);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (inn != null) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.welcome", inn.getName()), x + 8, y + 6, 0x404040, false);
            
            if (roomButtons.isEmpty()) {
                guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.no_rooms"), x + 8, y + 30, 0x404040, false);
            }
        } else {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.loading"), x + 8, y + 20, 0x404040, false);
        }
    }
}
