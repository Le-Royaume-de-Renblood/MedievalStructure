package fr.renblood.medievalstructures.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalstructures.MedievalStructures;
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
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class InnRoomsScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MedievalStructures.MODID, "textures/gui/inn_owner_gui.png");
    private Inn inn;
    private final Screen parent;
    private int imageWidth = 226;
    private int imageHeight = 166;
    private int page = 0;
    private final int itemsPerPage = 5;

    public InnRoomsScreen(Inn inn, Screen parent) {
        super(Component.translatable("gui.medieval_structures.rooms"));
        this.inn = inn;
        this.parent = parent;
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Bouton retour
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(x + 8, y + imageHeight - 28, 60, 20).build());

        // Boutons de pagination
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(x + 150, y + imageHeight - 28, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((page + 1) * itemsPerPage < inn.getRooms().size()) {
                page++;
                rebuildWidgets();
            }
        }).bounds(x + 180, y + imageHeight - 28, 20, 20).build());

        // Liste des chambres
        List<InnRoom> rooms = inn.getRooms();
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, rooms.size());

        for (int i = start; i < end; i++) {
            InnRoom room = rooms.get(i);
            int rowY = y + 20 + ((i - start) * 22);
            
            // Bouton Localiser
            addRenderableWidget(Button.builder(Component.translatable("gui.medieval_structures.locate"), button -> {
                if (room.getP1() != null && room.getP2() != null) {
                    ClientModEvents.setRoomVisualization(room.getP1(), room.getP2(), 100);
                    this.onClose();
                }
            }).bounds(x + 130, rowY, 40, 20).build());

            // Bouton Exclure (si occupée)
            if (room.isBusy()) {
                addRenderableWidget(Button.builder(Component.translatable("gui.medieval_structures.evict"), button -> {
                    PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 5, room.getNumber())); // Action 5 = Evict
                }).bounds(x + 175, rowY, 40, 20).build());
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 226, 166);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(this.font, this.title, x + 8, y + 6, 0x404040, false);
        
        List<InnRoom> rooms = inn.getRooms();
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, rooms.size());

        for (int i = start; i < end; i++) {
            InnRoom room = rooms.get(i);
            int rowY = y + 20 + ((i - start) * 22);
            
            String statusKey = room.isBusy() ? (room.isDirty() ? "gui.medieval_structures.status.dirty" : "gui.medieval_structures.status.occupied") : "gui.medieval_structures.status.free";
            Component status = room.isBusy() && !room.isDirty() ? 
                    Component.translatable(statusKey, room.getOccupantName()) : 
                    Component.translatable(statusKey);
            
            String text = "Ch. " + room.getNumber();
            guiGraphics.drawString(this.font, text, x + 8, rowY + 6, 0x404040, false);
            guiGraphics.drawString(this.font, status, x + 45, rowY + 6, room.isBusy() ? 0xAA0000 : 0x00AA00, false);
            
            // Affichage du solde (retard/avance)
            if (room.isBusy()) {
                int days = (int)(room.getBalance() / room.getPrice());
                String balanceText;
                int color;
                if (days < 0) {
                    balanceText = "Retard: " + Math.abs(days) + "j";
                    color = 0xAA0000;
                } else {
                    balanceText = "Avance: " + days + "j";
                    color = 0x00AA00;
                }
                guiGraphics.drawString(this.font, balanceText, x + 45, rowY + 14, color, false);
            }
        }
        
        // Page info
        String pageInfo = (page + 1) + " / " + (Math.max(1, (int)Math.ceil(rooms.size() / (double)itemsPerPage)));
        guiGraphics.drawCenteredString(this.font, pageInfo, x + 113, y + imageHeight - 22, 0x404040);
    }
}
