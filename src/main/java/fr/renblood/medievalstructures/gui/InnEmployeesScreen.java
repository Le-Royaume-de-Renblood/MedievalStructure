package fr.renblood.medievalstructures.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

public class InnEmployeesScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MedievalStructures.MODID, "textures/gui/inn_owner_gui.png");
    private Inn inn;
    private final Screen parent;
    private EditBox employeeNameBox;
    private int imageWidth = 226;
    private int imageHeight = 166;
    private Map<UUID, String> employeeNames;
    private String ownerName;

    public InnEmployeesScreen(Inn inn, Screen parent) {
        super(Component.translatable("gui.medieval_structures.employees"));
        this.inn = inn;
        this.parent = parent;
        this.employeeNames = inn.getEmployeeNames();
        this.ownerName = inn.getOwnerName();
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        this.employeeNames = inn.getEmployeeNames();
        this.ownerName = inn.getOwnerName();
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

        // Champ de texte pour ajouter un employé
        employeeNameBox = new EditBox(this.font, x + 8, y + 20, 100, 20, Component.translatable("gui.medieval_structures.employee_name"));
        addRenderableWidget(employeeNameBox);

        // Bouton ajouter
        addRenderableWidget(Button.builder(Component.translatable("gui.medieval_structures.add"), button -> {
            String name = employeeNameBox.getValue();
            if (!name.isEmpty()) {
                PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 3, 0, name)); 
                employeeNameBox.setValue("");
            }
        }).bounds(x + 112, y + 20, 20, 20).build());

        // Liste des employés
        int i = 0;
        for (UUID uuid : inn.getEmployees()) {
            if (i > 5) break;
            
            // Bouton supprimer pour chaque employé
            addRenderableWidget(Button.builder(Component.literal("X"), button -> {
                PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 4, 0, uuid.toString()));
            }).bounds(x + 180, y + 50 + (i * 22), 20, 20).build());
            i++;
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
        
        // Afficher le owner (corrigé pour afficher le nom)
        String ownerDisplay = (ownerName != null && !ownerName.isEmpty()) ? ownerName : inn.getOwnerUUID().toString();
        guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.owner", ownerDisplay), x + 8, y + 125, 0x404040, false);

        // Afficher les noms des employés
        int i = 0;
        for (UUID uuid : inn.getEmployees()) {
            if (i > 5) break;
            String name = employeeNames.getOrDefault(uuid, uuid.toString().substring(0, 8) + "...");
            guiGraphics.drawString(this.font, name, x + 8, y + 56 + (i * 22), 0x404040, false);
            i++;
        }
    }
}
