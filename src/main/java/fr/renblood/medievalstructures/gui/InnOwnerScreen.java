package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import fr.renblood.medievalstructures.client.economy.AdaptiveEconomyScreen;
import fr.renblood.medievalstructures.client.economy.EconomyUi;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class InnOwnerScreen extends AdaptiveEconomyScreen<InnOwnerMenu> {
    private static final int TEXT = 0xFFE8E0CC;
    private static final int MUTED = 0xFFBBAF96;
    private static final int GOLD = 0xFFFFC86B;
    private static final int GREEN = 0xFF73D17C;
    private static final int RED = 0xFFFF6B6B;

    private Inn inn;
    private Button toggleButton;
    private Button collectButton;
    private Button employeesButton;
    private Button viewPropsButton;
    private Button manageRoomsButton;
    private Button foodMenuButton;
    private Button routeButton;
    private Button payRentButton;
    private EditBox amountBox;

    public InnOwnerScreen(InnOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 420;
        this.imageHeight = 260;
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        this.imageWidth = Math.min(460, Math.max(360, this.width - 36));
        this.imageHeight = Math.min(340, Math.max(290, this.height - 32));
        super.init();
        if (inn == null) return;

        int x = leftPos;
        int y = topPos;
        int rightX = x + imageWidth - 148;

        if (isOwner() || isEmployee()) {
            toggleButton = addRenderableWidget(new ModernInnButton(rightX, y + 56, 130, 24, Component.literal("Activation"), this::onToggleActive, ModernInnButton.Style.PRIMARY));
            collectButton = addRenderableWidget(new ModernInnButton(rightX, y + 86, 130, 24, Component.literal("Collecter"), this::onCollectMoney, ModernInnButton.Style.SUCCESS));
            manageRoomsButton = addRenderableWidget(new ModernInnButton(rightX, y + 126, 130, 24, Component.literal("Chambres"), this::onManageRooms, ModernInnButton.Style.SECONDARY));
            viewPropsButton = addRenderableWidget(new ModernInnButton(rightX, y + 156, 130, 24, Component.literal("Maintenance"), this::onViewProps, ModernInnButton.Style.SECONDARY));
            foodMenuButton = addRenderableWidget(new ModernInnButton(rightX, y + 186, 130, 24, Component.literal("Menu"), this::onFoodMenu, ModernInnButton.Style.SECONDARY));
            employeesButton = addRenderableWidget(new ModernInnButton(rightX, y + 216, 130, 24, Component.literal("Employes"), this::onManageEmployees, ModernInnButton.Style.SECONDARY));
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2)) {
                routeButton = addRenderableWidget(new ModernInnButton(rightX, y + 246, 130, 24,
                        Component.literal("Route clients"), this::onRoute, ModernInnButton.Style.SECONDARY));
            }
        } else {
            amountBox = new EditBox(this.font, x + 22, y + 150, 122, 22, Component.literal("Montant"));
            amountBox.setMaxLength(10);
            addRenderableWidget(amountBox);
            payRentButton = addRenderableWidget(new ModernInnButton(x + 154, y + 149, 118, 24, Component.literal("Payer"), this::onPayRent, ModernInnButton.Style.SUCCESS));
        }
        if(isOwner()||isEmployee()) addRenderableWidget(new ModernInnButton(x+22,y+260,130,24,Component.literal("Carnet marchand"),button->{if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.connection.sendCommand("mc marchand");},ModernInnButton.Style.SECONDARY));
        updateButtonState();
    }

    private boolean isOwner() {
        return inn != null && Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(inn.getOwnerUUID());
    }

    private boolean isEmployee() {
        return inn != null && Minecraft.getInstance().player != null && inn.isEmployee(Minecraft.getInstance().player.getUUID());
    }

    private void onToggleActive(Button button) {
        if (inn != null) PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 0, 0));
    }

    private void onCollectMoney(Button button) {
        if (inn != null) PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 2, 0));
    }

    private void onManageEmployees(Button button) {
        if (inn != null) Minecraft.getInstance().setScreen(new InnEmployeesScreen(inn, this));
    }

    private void onViewProps(Button button) {
        if (inn != null) Minecraft.getInstance().setScreen(new InnPropsScreen(inn, this));
    }

    private void onManageRooms(Button button) {
        if (inn != null) Minecraft.getInstance().setScreen(new InnRoomsScreen(inn, this));
    }

    private void onFoodMenu(Button button) {
        if (inn != null) Minecraft.getInstance().setScreen(new InnFoodMenuScreen(inn, this));
    }

    private void onRoute(Button button) {
        if (inn != null) Minecraft.getInstance().setScreen(new InnRouteSelectionScreen(inn, this));
    }

    private void onPayRent(Button button) {
        if (inn == null || amountBox == null || Minecraft.getInstance().player == null) return;
        try {
            double amount = Double.parseDouble(amountBox.getValue());
            if (amount <= 0) return;
            for (InnRoom room : inn.getRooms()) {
                if (room.getOccupantUUID() != null && room.getOccupantUUID().equals(Minecraft.getInstance().player.getUUID())) {
                    PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 6, room.getNumber(), String.valueOf(amount)));
                    amountBox.setValue("");
                    return;
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        this.clearWidgets();
        this.init();
    }

    private void updateButtonState() {
        if (toggleButton != null && inn != null) {
            toggleButton.setMessage(Component.literal(inn.isActive() ? "Desactiver" : "Activer"));
            toggleButton.active = isOwner();
        }
        if (collectButton != null && inn != null) {
            collectButton.active = isOwner();
            collectButton.setMessage(Component.literal("Virer la caisse en banque"));
        }
        if (employeesButton != null && inn != null) employeesButton.active = isOwner();
        if (foodMenuButton != null && inn != null) foodMenuButton.active = true;
        if (viewPropsButton != null && inn != null) viewPropsButton.active = true;
        if (manageRoomsButton != null && inn != null) manageRoomsButton.active = true;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(guiGraphics);
    }

    @Override
    public void renderLogical(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.renderLogical(guiGraphics, mouseX, mouseY, partialTick);
        renderContent(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics guiGraphics) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xAA000000);
        guiGraphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xF01F1B17, 0xF0111517);
        guiGraphics.fill(x, y, x + imageWidth, y + 36, 0xFF2D241B);
        guiGraphics.fill(x, y + 36, x + imageWidth, y + 37, 0xFF6B4D25);
    }

    private void renderContent(GuiGraphics guiGraphics) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(this.font, inn != null ? trim(inn.getName(), 32) : "Auberge", x + 18, y + 14, GOLD, false);
        if (inn == null) {
            guiGraphics.drawString(this.font, "Chargement...", x + 18, y + 58, MUTED, false);
            return;
        }

        if (isOwner() || isEmployee()) {
            renderStaffDashboard(guiGraphics, x, y);
        } else {
            renderGuestDashboard(guiGraphics, x, y);
        }
    }

    private void renderStaffDashboard(GuiGraphics guiGraphics, int x, int y) {
        long freeRooms = inn.getRooms().stream().filter(r -> !r.isBusy() && !r.isDirty()).count();
        long occupiedRooms = inn.getRooms().stream().filter(InnRoom::isBusy).count();
        long dirtyProps = inn.getProps().stream().filter(p -> !p.isActive()).count();

        guiGraphics.drawString(this.font, inn.isActive() ? "Ouverte" : "Fermee", x + imageWidth - 92, y + 14, inn.isActive() ? GREEN : RED, false);
        guiGraphics.drawString(this.font, "Caisse confirmée", x + 22, y + 54, MUTED, false);
        guiGraphics.drawString(this.font, "Consulter Mes finances", x + 22, y + 68, GOLD, false);
        guiGraphics.drawString(this.font, "Service", x + 22, y + 90, MUTED, false);
        guiGraphics.drawString(this.font, inn.getTotalMenuSales() + " ventes | "
                + MedievalCoinsIntegration.formatMoney(inn.getTotalMenuRevenue()), x + 22, y + 104, GOLD, false);
        if (!inn.getLastMenuSaleItem().isBlank()) {
            guiGraphics.drawString(this.font, trim("Dernier: " + inn.getLastMenuSaleItem() + " (" + inn.getLastMenuSalePrice() + ")", 48), x + 22, y + 118, TEXT, false);
        }
        guiGraphics.drawString(this.font, "Chambres", x + 22, y + 144, MUTED, false);
        guiGraphics.drawString(this.font, inn.getRooms().size() + " total  |  " + freeRooms + " libres  |  " + occupiedRooms + " occupees", x + 22, y + 158, TEXT, false);
        guiGraphics.drawString(this.font, "Maintenance", x + 22, y + 184, MUTED, false);
        guiGraphics.drawString(this.font, dirtyProps + " elements a verifier sur " + inn.getProps().size(), x + 22, y + 198, dirtyProps > 0 ? RED : GREEN, false);
        guiGraphics.drawString(this.font, "Menu", x + 22, y + 224, MUTED, false);
        guiGraphics.drawString(this.font, trim(inn.getMenuEntries().size() + " items | " + readinessText(), 58), x + 22, y + 238, inn.canReceiveCustomers() ? GREEN : RED, false);
    }

    private String readinessText() {
        if (!inn.hasCustomerRoute()) return "route client manquante";
        return inn.countMenuType(fr.renblood.medievalstructures.inn.InnMenuEntry.Type.DISH) + "/4 plats, "
                + inn.countMenuType(fr.renblood.medievalstructures.inn.InnMenuEntry.Type.DRINK) + "/2 boissons, "
                + inn.countMenuType(fr.renblood.medievalstructures.inn.InnMenuEntry.Type.STARTER) + "/2 starters";
    }

    private void renderGuestDashboard(GuiGraphics guiGraphics, int x, int y) {
        InnRoom playerRoom = null;
        if (Minecraft.getInstance().player != null) {
            for (InnRoom room : inn.getRooms()) {
                if (room.getOccupantUUID() != null && room.getOccupantUUID().equals(Minecraft.getInstance().player.getUUID())) {
                    playerRoom = room;
                    break;
                }
            }
        }

        if (playerRoom == null) {
            guiGraphics.drawString(this.font, "Aucune chambre louee ici.", x + 22, y + 60, MUTED, false);
            guiGraphics.drawString(this.font, "Les chambres disponibles sont proposees depuis le comptoir client.", x + 22, y + 76, TEXT, false);
            return;
        }

        int days = playerRoom.getPrice() > 0 ? (int) (playerRoom.getBalance() / playerRoom.getPrice()) : 0;
        guiGraphics.drawString(this.font, "Votre chambre", x + 22, y + 56, MUTED, false);
        guiGraphics.drawString(this.font, "Chambre " + playerRoom.getNumber(), x + 22, y + 72, GOLD, false);
        guiGraphics.drawString(this.font, "Prix / jour: " + (int) playerRoom.getPrice(), x + 22, y + 94, TEXT, false);
        guiGraphics.drawString(this.font, days < 0 ? "Retard: " + Math.abs(days) + " jours" : "Avance: " + days + " jours", x + 22, y + 110, days < 0 ? RED : GREEN, false);
        guiGraphics.drawString(this.font, "Ajouter un paiement", x + 22, y + 134, MUTED, false);
    }

    private String trim(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
    @Override protected String economyResource(){return inn==null?"transaction":"inn:"+inn.getEconomyId();}
}
