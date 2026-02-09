package fr.renblood.medievalstructures.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnProp;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class InnOwnerScreen extends AbstractContainerScreen<InnOwnerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MedievalStructures.MODID, "textures/gui/inn_owner_gui.png");
    private Inn inn;
    private Button toggleButton;
    private Button collectButton;
    private Button employeesButton;
    private Button viewPropsButton;
    private Button manageRoomsButton;
    
    // Pour les clients
    private Button payRentButton;
    private EditBox amountBox;

    public InnOwnerScreen(InnOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 226;
        this.imageHeight = 166;
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Si propriétaire ou employé
        if (inn != null && (isOwner() || isEmployee())) {
            toggleButton = Button.builder(Component.translatable("gui.medieval_structures.loading"), this::onToggleActive)
                    .bounds(x + 150, y + 16, 70, 20)
                    .build();
            toggleButton.active = false;
            addRenderableWidget(toggleButton);

            collectButton = Button.builder(Component.translatable("gui.medieval_structures.collect"), this::onCollectMoney)
                    .bounds(x + 150, y + 40, 70, 20)
                    .build();
            collectButton.active = false;
            addRenderableWidget(collectButton);

            employeesButton = Button.builder(Component.translatable("gui.medieval_structures.manage_employees"), this::onManageEmployees)
                    .bounds(x + 150, y + 64, 70, 20)
                    .build();
            employeesButton.active = false;
            addRenderableWidget(employeesButton);
            
            viewPropsButton = Button.builder(Component.translatable("gui.medieval_structures.view_props"), this::onViewProps)
                    .bounds(x + 150, y + 88, 70, 20)
                    .build();
            viewPropsButton.active = false;
            addRenderableWidget(viewPropsButton);

            manageRoomsButton = Button.builder(Component.translatable("gui.medieval_structures.manage_rooms"), this::onManageRooms)
                    .bounds(x + 150, y + 112, 70, 20)
                    .build();
            manageRoomsButton.active = false;
            addRenderableWidget(manageRoomsButton);
        } 
        // Si client
        else if (inn != null) {
            amountBox = new EditBox(this.font, x + 63, y + 60, 100, 20, Component.translatable("gui.medieval_structures.amount"));
            addRenderableWidget(amountBox);
            
            payRentButton = Button.builder(Component.translatable("gui.medieval_structures.pay_rent"), this::onPayRent)
                    .bounds(x + 63, y + 85, 100, 20)
                    .build();
            addRenderableWidget(payRentButton);
        }
        
        updateButtonState();
    }
    
    private boolean isOwner() {
        return inn != null && Minecraft.getInstance().player.getUUID().equals(inn.getOwnerUUID());
    }
    
    private boolean isEmployee() {
        return inn != null && inn.isEmployee(Minecraft.getInstance().player.getUUID());
    }

    private void onToggleActive(Button button) {
        if (inn != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 0, 0));
            // On attend la réponse du serveur pour mettre à jour l'état local via updateInnData
        }
    }

    private void onCollectMoney(Button button) {
        if (inn != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 2, 0));
        }
    }

    private void onManageEmployees(Button button) {
        if (inn != null) {
            Minecraft.getInstance().setScreen(new InnEmployeesScreen(inn, this));
        }
    }
    
    private void onViewProps(Button button) {
        if (inn != null) {
            Minecraft.getInstance().setScreen(new InnPropsScreen(inn, this));
        }
    }

    private void onManageRooms(Button button) {
        if (inn != null) {
            Minecraft.getInstance().setScreen(new InnRoomsScreen(inn, this));
        }
    }
    
    private void onPayRent(Button button) {
        if (inn != null) {
            try {
                double amount = Double.parseDouble(amountBox.getValue());
                if (amount > 0) {
                    // Trouver la chambre du joueur
                    for (InnRoom room : inn.getRooms()) {
                        if (room.getOccupantUUID() != null && room.getOccupantUUID().equals(Minecraft.getInstance().player.getUUID())) {
                            PacketHandler.INSTANCE.sendToServer(new PacketInnAction(inn.getName(), 6, room.getNumber(), String.valueOf(amount)));
                            amountBox.setValue("");
                            return;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // Ignorer
            }
        }
    }

    public void updateInnData(Inn inn) {
        this.inn = inn;
        // Re-init si le statut change (ex: devient employé) ou si les données changent
        this.clearWidgets();
        this.init();
    }

    private void updateButtonState() {
        if (toggleButton != null && inn != null) {
            toggleButton.setMessage(Component.translatable(inn.isActive() ? "gui.medieval_structures.deactivate" : "gui.medieval_structures.activate"));
            toggleButton.active = true;
        }
        if (collectButton != null && inn != null) {
            collectButton.active = inn.getBalance() > 0;
            collectButton.setMessage(Component.translatable("gui.medieval_structures.collect_amount", (int)inn.getBalance()));
        }
        if (employeesButton != null && inn != null) {
            employeesButton.active = isOwner();
        }
        if (viewPropsButton != null && inn != null) {
            viewPropsButton.active = true;
        }
        if (manageRoomsButton != null && inn != null) {
            manageRoomsButton.active = true;
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
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
            
            guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.inn_name", inn.getName()), x + 8, y + 6, 0x404040, false);
            
            if (isOwner() || isEmployee()) {
                // Affichage Admin
                guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.rooms_count", inn.getRooms().size()), x + 8, y + 20, 0x404040, false);
                int i = 0;
                for (InnRoom room : inn.getRooms()) {
                    if (i > 5) break; 
                    String statusKey = room.isBusy() ? (room.isDirty() ? "gui.medieval_structures.status.dirty" : "gui.medieval_structures.status.occupied") : "gui.medieval_structures.status.free";
                    Component status = room.isBusy() && !room.isDirty() ? 
                            Component.translatable(statusKey, room.getOccupantName()) : 
                            Component.translatable(statusKey);
                    
                    guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.room_status", room.getNumber(), status), x + 8, y + 32 + (i * 10), 0x404040, false);
                    i++;
                }

                int propsY = y + 32 + (6 * 10) + 10;
                guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.props_count", inn.getProps().size()), x + 8, propsY, 0x404040, false);
                int j = 0;
                for (InnProp prop : inn.getProps()) {
                    if (j > 4) break;
                    String stateKey = prop.isActive() ? "gui.medieval_structures.status.ok" : "gui.medieval_structures.status.maintenance";
                    guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.prop_status", prop.getType(), prop.getId(), Component.translatable(stateKey)), x + 8, propsY + 12 + (j * 10), prop.isActive() ? 0x00AA00 : 0xAA0000, false);
                    j++;
                }
            } else {
                // Affichage Client
                InnRoom playerRoom = null;
                for (InnRoom room : inn.getRooms()) {
                    if (room.getOccupantUUID() != null && room.getOccupantUUID().equals(Minecraft.getInstance().player.getUUID())) {
                        playerRoom = room;
                        break;
                    }
                }
                
                if (playerRoom != null) {
                    guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.your_room", playerRoom.getNumber()), x + 8, y + 20, 0x404040, false);
                    guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.price_per_day", playerRoom.getPrice()), x + 8, y + 32, 0x404040, false);
                    
                    int days = (int)(playerRoom.getBalance() / playerRoom.getPrice());
                    Component balanceText;
                    int color;
                    if (days < 0) {
                        balanceText = Component.translatable("gui.medieval_structures.payment_late", Math.abs(days), (int)Math.abs(playerRoom.getBalance()));
                        color = 0xAA0000;
                    } else {
                        balanceText = Component.translatable("gui.medieval_structures.payment_advance", days);
                        color = 0x00AA00;
                    }
                    guiGraphics.drawString(this.font, balanceText, x + 8, y + 44, color, false);
                } else {
                    guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.no_room_rented"), x + 8, y + 20, 0x404040, false);
                }
            }

        } else {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.loading"), x + 8, y + 20, 0x404040, false);
        }
    }
}
