package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;

public class InnEmployeesScreen extends Screen {
    private static final int TEXT = 0xFFE8E0CC;
    private static final int MUTED = 0xFFBBAF96;
    private static final int GOLD = 0xFFFFC86B;
    private Inn inn;
    private final Screen parent;
    private EditBox employeeNameBox;
    private int imageWidth = 420;
    private int imageHeight = 260;
    private int left;
    private int top;
    private Map<UUID, String> employeeNames;
    private String ownerName;

    public InnEmployeesScreen(Inn inn, Screen parent) {
        super(Component.literal("Employes"));
        this.inn = inn;
        this.parent = parent;
        this.employeeNames = inn.getEmployeeNames();
        this.ownerName = inn.getOwnerName();
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        this.employeeNames = inn.getEmployeeNames();
        this.ownerName = inn.getOwnerName();
        rebuildWidgets();
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
        employeeNameBox = new EditBox(this.font, left + 22, top + 60, 160, 22, Component.literal("Pseudo"));
        employeeNameBox.setMaxLength(32);
        addRenderableWidget(employeeNameBox);
        addRenderableWidget(new ModernInnButton(left + 192, top + 59, 88, 24, Component.literal("Ajouter"), b -> {
            String name = employeeNameBox.getValue().trim();
            if (!name.isEmpty()) {
                PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 3, 0, name));
                employeeNameBox.setValue("");
            }
        }, ModernInnButton.Style.SUCCESS));

        int i = 0;
        for (UUID uuid : inn.getEmployees()) {
            if (i >= 6) break;
            int rowY = top + 104 + i * 28;
            addRenderableWidget(new ModernInnButton(left + imageWidth - 92, rowY - 5, 70, 22, Component.literal("Retirer"), b ->
                    PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 4, 0, uuid.toString())), ModernInnButton.Style.DANGER));
            i++;
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
        guiGraphics.drawString(this.font, "Employes - " + inn.getName(), left + 18, top + 14, GOLD, false);
        String ownerDisplay = ownerName != null && !ownerName.isEmpty() ? ownerName : inn.getOwnerUUID().toString();
        guiGraphics.drawString(this.font, "Proprietaire: " + ownerDisplay, left + 22, top + 44, MUTED, false);
        guiGraphics.drawString(this.font, "Ajouter un employe connecte", left + 22, top + 88, MUTED, false);

        int i = 0;
        for (UUID uuid : inn.getEmployees()) {
            if (i >= 6) break;
            int rowY = top + 104 + i * 28;
            String name = employeeNames.getOrDefault(uuid, uuid.toString().substring(0, 8) + "...");
            guiGraphics.fill(left + 18, rowY - 8, left + imageWidth - 18, rowY + 18, 0x552B2B2B);
            guiGraphics.drawString(this.font, name, left + 26, rowY, TEXT, false);
            i++;
        }
        if (inn.getEmployees().isEmpty()) {
            guiGraphics.drawString(this.font, "Aucun employe.", left + 22, top + 112, MUTED, false);
        }
    }
}
