package fr.renblood.medievalstructures.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.event.ClientModEvents;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnProp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InnPropsScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MedievalStructures.MODID, "textures/gui/inn_owner_gui.png");
    private final Inn inn;
    private final Screen parent;
    private int imageWidth = 226;
    private int imageHeight = 166;
    private int page = 0;
    private final int itemsPerPage = 5;
    private List<InnProp> dirtyProps;

    public InnPropsScreen(Inn inn, Screen parent) {
        super(Component.translatable("gui.medieval_structures.props"));
        this.inn = inn;
        this.parent = parent;
        this.dirtyProps = inn.getProps().stream().filter(p -> !p.isActive()).collect(Collectors.toList());
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

        // Bouton "Tout voir" (Voir tous les props sales)
        addRenderableWidget(Button.builder(Component.literal("Tout voir"), button -> {
            List<BlockPos> positions = new ArrayList<>();
            for (InnProp prop : dirtyProps) {
                positions.add(prop.getPos());
            }
            ClientModEvents.setHighlightedProps(positions, 600); // 30 secondes (600 ticks)
            this.onClose();
        }).bounds(x + 75, y + imageHeight - 28, 70, 20).build());

        // Boutons de pagination
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(x + 150, y + imageHeight - 28, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((page + 1) * itemsPerPage < dirtyProps.size()) {
                page++;
                rebuildWidgets();
            }
        }).bounds(x + 180, y + imageHeight - 28, 20, 20).build());

        // Liste des props sales
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, dirtyProps.size());

        for (int i = start; i < end; i++) {
            InnProp prop = dirtyProps.get(i);
            int rowY = y + 20 + ((i - start) * 22);
            
            // Bouton Localiser
            addRenderableWidget(Button.builder(Component.translatable("gui.medieval_structures.locate"), button -> {
                ClientModEvents.setHighlightedProps(Collections.singletonList(prop.getPos()), 600); // 30 secondes
                this.onClose();
            }).bounds(x + 160, rowY, 50, 20).build());
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
        
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, dirtyProps.size());

        if (dirtyProps.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Aucun prop à nettoyer !", x + imageWidth / 2, y + imageHeight / 2, 0x404040);
        } else {
            for (int i = start; i < end; i++) {
                InnProp prop = dirtyProps.get(i);
                int rowY = y + 20 + ((i - start) * 22);
                
                String stateKey = "gui.medieval_structures.status.maintenance";
                Component status = Component.translatable(stateKey);
                
                String text = "ID: " + prop.getId() + " (" + prop.getType() + ")";
                guiGraphics.drawString(this.font, text, x + 8, rowY + 6, 0x404040, false);
                guiGraphics.drawString(this.font, status, x + 100, rowY + 6, 0xAA0000, false);
            }
        }
        
        // Page info
        String pageInfo = (page + 1) + " / " + (Math.max(1, (int)Math.ceil(dirtyProps.size() / (double)itemsPerPage)));
        guiGraphics.drawCenteredString(this.font, pageInfo, x + 113, y + imageHeight - 22, 0x404040);
    }
}
