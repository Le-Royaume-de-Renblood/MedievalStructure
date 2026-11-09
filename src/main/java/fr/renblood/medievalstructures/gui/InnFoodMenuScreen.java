package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnMenuEntry;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnMenuAction;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class InnFoodMenuScreen extends Screen {
    private static final int TEXT = 0xFFE8E0CC;
    private static final int MUTED = 0xFFBBAF96;
    private static final int GOLD = 0xFFFFC86B;
    private static final int GREEN = 0xFF73D17C;
    private static final int RED = 0xFFFF6B6B;

    private Inn inn;
    private final Screen parent;
    private int selectedIndex = 0;
    private int listOffset = 0;

    public InnFoodMenuScreen(Inn inn, Screen parent) {
        super(Component.literal("Menu auberge"));
        this.inn = inn;
        this.parent = parent;
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        if (parent instanceof InnOwnerScreen ownerScreen) {
            ownerScreen.updateInnData(inn);
        }
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void init() {
        int panelW = Math.min(520, this.width - 32);
        int panelH = Math.min(320, this.height - 32);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int rightX = x + panelW - 142;

        addRenderableWidget(new ModernInnButton(rightX, y + 48, 38, 20, Component.literal("Plat"),
                b -> addFromHand(InnMenuEntry.Type.DISH), ModernInnButton.Style.PRIMARY));
        addRenderableWidget(new ModernInnButton(rightX + 43, y + 48, 38, 20, Component.literal("Boiss."),
                b -> addFromHand(InnMenuEntry.Type.DRINK), ModernInnButton.Style.PRIMARY));
        addRenderableWidget(new ModernInnButton(rightX + 86, y + 48, 38, 20, Component.literal("Start."),
                b -> addFromHand(InnMenuEntry.Type.STARTER), ModernInnButton.Style.PRIMARY));
        addRenderableWidget(new ModernInnButton(rightX, y + 76, 124, 22, Component.literal("Catalogue"),
                b -> Minecraft.getInstance().setScreen(new InnReferenceCatalogScreen(inn, this)), ModernInnButton.Style.SECONDARY));

        addRenderableWidget(new ModernInnButton(rightX, y + 106, 38, 22, Component.literal("-5%"),
                b -> adjustSelected("-"), ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(rightX + 44, y + 106, 38, 22, Component.literal("+5%"),
                b -> adjustSelected("+"), ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(rightX + 88, y + 106, 36, 22, Component.literal("Del"),
                b -> removeSelected(), ModernInnButton.Style.DANGER));

        int typeButtonY = y + 134;
        int returnButtonY = y + panelH - 34;

        addRenderableWidget(new ModernInnButton(rightX, typeButtonY, 124, 22, Component.literal("Type suivant"),
                b -> rotateSelectedType(), ModernInnButton.Style.SECONDARY));
        if (panelH >= 250) {
            int scrollButtonY = y + panelH - 62;
            addRenderableWidget(new ModernInnButton(rightX, scrollButtonY, 58, 22, Component.literal("Haut"),
                    b -> scrollList(-1), ModernInnButton.Style.SECONDARY));
            addRenderableWidget(new ModernInnButton(rightX + 66, scrollButtonY, 58, 22, Component.literal("Bas"),
                    b -> scrollList(1), ModernInnButton.Style.SECONDARY));
        }
        addRenderableWidget(new ModernInnButton(rightX, returnButtonY, 124, 22, Component.literal("Retour"),
                b -> Minecraft.getInstance().setScreen(parent), ModernInnButton.Style.SECONDARY));

        boolean routeReady = inn.hasCustomerRoute();
        for (var widget : this.renderables) {
            if (widget instanceof Button button && !button.getMessage().getString().equals("Retour")) {
                button.active = routeReady;
            }
        }
    }

    private void addFromHand(InnMenuEntry.Type type) {
        PacketHandler.INSTANCE.sendToServer(new PacketInnMenuAction(inn.getName(), PacketInnMenuAction.ADD_FROM_HAND, "", type.name()));
    }

    private void adjustSelected(String direction) {
        InnMenuEntry selected = selectedEntry();
        if (selected != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketInnMenuAction(inn.getName(), PacketInnMenuAction.MARKUP, selected.getItemId(), direction));
        }
    }

    private void removeSelected() {
        InnMenuEntry selected = selectedEntry();
        if (selected != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketInnMenuAction(inn.getName(), PacketInnMenuAction.REMOVE, selected.getItemId(), ""));
            selectedIndex = Math.max(0, selectedIndex - 1);
        }
    }

    private void rotateSelectedType() {
        InnMenuEntry selected = selectedEntry();
        if (selected == null) return;
        InnMenuEntry.Type[] types = InnMenuEntry.Type.values();
        InnMenuEntry.Type next = types[(selected.getType().ordinal() + 1) % types.length];
        PacketHandler.INSTANCE.sendToServer(new PacketInnMenuAction(inn.getName(), PacketInnMenuAction.TYPE, selected.getItemId(), next.name()));
    }

    private InnMenuEntry selectedEntry() {
        List<InnMenuEntry> entries = inn.getMenuEntries();
        if (entries.isEmpty()) return null;
        selectedIndex = Math.max(0, Math.min(selectedIndex, entries.size() - 1));
        return entries.get(selectedIndex);
    }

    private void scrollList(int delta) {
        int maxRows = visibleRows();
        int maxOffset = Math.max(0, inn.getMenuEntries().size() - maxRows);
        listOffset = Math.max(0, Math.min(maxOffset, listOffset + delta));
        selectedIndex = Math.max(listOffset, Math.min(selectedIndex, listOffset + maxRows - 1));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelW = Math.min(520, this.width - 32);
        int panelH = Math.min(320, this.height - 32);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        graphics.fill(x - 2, y - 2, x + panelW + 2, y + panelH + 2, 0xAA000000);
        graphics.fillGradient(x, y, x + panelW, y + panelH, 0xF01F1B17, 0xF0111517);
        graphics.fill(x, y, x + panelW, y + 36, 0xFF2D241B);
        graphics.fill(x, y + 36, x + panelW, y + 37, 0xFF6B4D25);

        graphics.drawString(this.font, trim("Menu - " + inn.getName(), 34), x + 16, y + 14, GOLD, false);
        graphics.drawString(this.font, readinessText(), x + 16, y + 46, inn.canReceiveCustomers() ? GREEN : RED, false);
        if (!inn.hasCustomerRoute()) {
            graphics.drawString(this.font, "Contactez un admin pour definir une route client.", x + 16, y + 60, RED, false);
        }

        List<InnMenuEntry> entries = inn.getMenuEntries();
        int listX = x + 16;
        int listY = y + 82;
        int rowH = 22;
        int maxRows = visibleRows();
        listOffset = Math.max(0, Math.min(listOffset, Math.max(0, entries.size() - maxRows)));
        for (int i = 0; i < Math.min(maxRows, entries.size() - listOffset); i++) {
            int entryIndex = i + listOffset;
            InnMenuEntry entry = entries.get(entryIndex);
            int rowY = listY + i * rowH;
            int bg = entryIndex == selectedIndex ? 0x553C6E71 : 0x33100000;
            graphics.fill(listX, rowY - 2, x + panelW - 158, rowY + 18, bg);
            graphics.drawString(this.font, trim(entry.getDisplayName(), 20), listX + 6, rowY, TEXT, false);
            graphics.drawString(this.font, entry.getType().displayName(), listX + 156, rowY, MUTED, false);
            graphics.drawString(this.font, entry.getMarkupPercent() + "%", listX + 230, rowY, entry.getMarkupPercent() >= 0 ? GREEN : RED, false);
            graphics.drawString(this.font, MedievalCoinsIntegration.formatMoney(entry.getFinalPrice()), listX + 278, rowY, GOLD, false);
        }

        if (entries.isEmpty()) {
            graphics.drawString(this.font, "Tenez une nourriture en main puis choisissez son type.", listX + 6, listY, MUTED, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String readinessText() {
        return inn.countMenuType(InnMenuEntry.Type.DISH) + "/4 plats  "
                + inn.countMenuType(InnMenuEntry.Type.DRINK) + "/2 boissons  "
                + inn.countMenuType(InnMenuEntry.Type.STARTER) + "/2 starters";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = Math.min(520, this.width - 32);
        int panelH = Math.min(320, this.height - 32);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int listX = x + 16;
        int listY = y + 82;
        int rowH = 22;
        int maxRows = visibleRows();
        for (int i = 0; i < Math.min(maxRows, inn.getMenuEntries().size() - listOffset); i++) {
            int rowY = listY + i * rowH;
            if (mouseX >= listX && mouseX <= x + panelW - 158 && mouseY >= rowY - 2 && mouseY <= rowY + 18) {
                selectedIndex = i + listOffset;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollList(delta > 0 ? -1 : 1);
        return true;
    }

    private int visibleRows() {
        int panelH = Math.min(320, this.height - 32);
        return Math.max(1, Math.min(8, (panelH - 104) / 22));
    }

    private String trim(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
