package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.inn.InnReferenceItem;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnReferenceAdminAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class InnReferenceCreationScreen extends Screen {
    private final InnReferenceItem reference;
    private final Screen parent;
    private EditBox minBox;
    private EditBox maxBox;
    private boolean sendToBackend = true;
    private String status = "Calcul de la fourchette depuis les recettes...";
    private int refreshCountdown = -1;

    public InnReferenceCreationScreen(InnReferenceItem reference, Screen parent) {
        super(Component.literal("Creer une reference"));
        this.reference = reference;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelW = Math.max(300, Math.min(470, width - 32));
        int x = (width - panelW) / 2;
        int y = Math.max(16, (height - 230) / 2);
        minBox = numericBox(x + 30, y + 92, (panelW - 70) / 2, "Minimum");
        maxBox = numericBox(x + 40 + (panelW - 70) / 2, y + 92, (panelW - 70) / 2, "Maximum");
        addRenderableWidget(minBox);
        addRenderableWidget(maxBox);
        addRenderableWidget(new ModernInnButton(x + 30, y + 132, panelW - 60, 22,
                Component.literal("Backend MedievalCoins: oui"), button -> {
                    sendToBackend = !sendToBackend;
                    button.setMessage(Component.literal("Backend MedievalCoins: " + (sendToBackend ? "oui" : "non")));
                }, ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(x + 30, y + 168, (panelW - 70) / 2, 22,
                Component.literal("Creer"), button -> create(), ModernInnButton.Style.SUCCESS));
        addRenderableWidget(new ModernInnButton(x + 40 + (panelW - 70) / 2, y + 168, (panelW - 70) / 2, 22,
                Component.literal("Retour"), button -> Minecraft.getInstance().setScreen(parent), ModernInnButton.Style.SECONDARY));
        PacketHandler.INSTANCE.sendToServer(new PacketInnReferenceAdminAction(reference.itemId(), 0, 0, true, false));
    }

    private EditBox numericBox(int x, int y, int width, String hint) {
        EditBox box = new EditBox(font, x, y, width, 22, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(10);
        box.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        return box;
    }

    public void applySuggestion(String itemId, int min, int max, boolean created) {
        if (!reference.itemId().equals(itemId)) return;
        if (created) {
            status = "Reference creee. Actualisation du catalogue...";
            refreshCountdown = 60;
            return;
        }
        if (min > 0 && max >= min) {
            minBox.setValue(Integer.toString(min));
            maxBox.setValue(Integer.toString(max));
            status = "Fourchette suggeree depuis une recette entierement referencee.";
        } else {
            status = "Aucune recette entierement referencee: saisie manuelle requise.";
        }
    }

    private void create() {
        try {
            int min = Integer.parseInt(minBox.getValue());
            int max = Integer.parseInt(maxBox.getValue());
            if (min < 1 || max < min) {
                status = "Le maximum doit etre superieur ou egal au minimum.";
                return;
            }
            status = "Enregistrement...";
            PacketHandler.INSTANCE.sendToServer(new PacketInnReferenceAdminAction(reference.itemId(), min, max,
                    sendToBackend, true));
        } catch (NumberFormatException exception) {
            status = "Renseignez deux prix entiers valides.";
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshCountdown < 0) return;
        if (--refreshCountdown == 0) {
            if (parent instanceof InnReferenceCatalogScreen catalog) {
                catalog.refreshReferences();
            }
            Minecraft.getInstance().setScreen(parent);
            refreshCountdown = -1;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelW = Math.max(300, Math.min(470, width - 32));
        int x = (width - panelW) / 2;
        int y = Math.max(16, (height - 230) / 2);
        graphics.fill(x - 2, y - 2, x + panelW + 2, y + 230, 0xAA000000);
        graphics.fillGradient(x, y, x + panelW, y + 228, 0xF01F1B17, 0xF0111517);
        graphics.fill(x, y, x + panelW, y + 36, 0xFF2D241B);
        graphics.drawString(font, "Nouvelle reference de prix", x + 18, y + 14, 0xFFFFC86B, false);
        graphics.drawString(font, reference.displayName(), x + 30, y + 52, 0xFFE8E0CC, false);
        graphics.drawString(font, reference.itemId(), x + 30, y + 68, 0xFFBBAF96, false);
        var statusLines = font.split(Component.literal(status), panelW - 60);
        for (int i = 0; i < Math.min(2, statusLines.size()); i++) {
            graphics.drawString(font, statusLines.get(i), x + 30, y + 202 + i * 10, 0xFFBBAF96, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
