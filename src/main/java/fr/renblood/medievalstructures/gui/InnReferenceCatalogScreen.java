package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnMenuEntry;
import fr.renblood.medievalstructures.inn.InnReferenceItem;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnMenuAction;
import fr.renblood.medievalstructures.network.PacketRequestInnReferences;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

public class InnReferenceCatalogScreen extends Screen {
    private static final int TEXT = 0xFFE8E0CC;
    private static final int MUTED = 0xFFBBAF96;
    private static final int GOLD = 0xFFFFC86B;
    private static final int GREEN = 0xFF73D17C;
    private static final int RED = 0xFFFF6B6B;

    private Inn inn;
    private final InnFoodMenuScreen parent;
    private List<InnReferenceItem> references = List.of();
    private EditBox search;
    private String query = "";
    private int offset;
    private int selectedIndex = -1;
    private boolean requested;
    private Filter filter = Filter.ALL;

    private enum Filter {
        ALL("Tous"), REFERENCED("References"), MISSING("Sans reference"), FOOD("Alimentaires");
        private final String label;
        Filter(String label) { this.label = label; }
    }

    public InnReferenceCatalogScreen(Inn inn, InnFoodMenuScreen parent) {
        super(Component.literal("Catalogue des prix de reference"));
        this.inn = inn;
        this.parent = parent;
    }

    public InnReferenceCatalogScreen() {
        super(Component.literal("Catalogue admin des trades"));
        this.inn = null;
        this.parent = null;
    }

    public void updateReferences(List<InnReferenceItem> references) {
        this.references = List.copyOf(references);
        this.offset = 0;
        this.selectedIndex = -1;
    }

    public void refreshReferences() {
        PacketHandler.INSTANCE.sendToServer(new PacketRequestInnReferences(true));
    }

    public void updateInnData(Inn updatedInn) {
        this.inn = updatedInn;
        if (parent != null) parent.updateInnData(updatedInn);
    }

    @Override
    protected void init() {
        int panelW = panelWidth();
        int panelH = panelHeight();
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        search = new EditBox(font, x + 16, y + 45, panelW - 32, 20, Component.literal("Rechercher"));
        search.setHint(Component.literal("Rechercher un nom, un identifiant ou une categorie..."));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            offset = 0;
            selectedIndex = -1;
        });
        addRenderableWidget(search);

        int filterGap = 4;
        int filterW = (panelW - 32 - filterGap * 3) / 4;
        int filterY = y + 70;
        for (int i = 0; i < Filter.values().length; i++) {
            Filter value = Filter.values()[i];
            addRenderableWidget(new ModernInnButton(x + 16 + i * (filterW + filterGap), filterY, filterW, 20,
                    Component.literal(value.label), button -> {
                        filter = value;
                        offset = 0;
                        selectedIndex = -1;
                    }, value == filter ? ModernInnButton.Style.PRIMARY : ModernInnButton.Style.SECONDARY));
        }

        if (inn != null) {
            int controlsY = y + panelH - 56;
            int gap = 6;
            int buttonW = (panelW - 32 - gap * 2) / 3;
            addRenderableWidget(new ModernInnButton(x + 16, controlsY, buttonW, 22, Component.literal("Ajouter: plat"),
                    button -> addSelected(InnMenuEntry.Type.DISH), ModernInnButton.Style.PRIMARY));
            addRenderableWidget(new ModernInnButton(x + 16 + buttonW + gap, controlsY, buttonW, 22, Component.literal("Ajouter: boisson"),
                    button -> addSelected(InnMenuEntry.Type.DRINK), ModernInnButton.Style.PRIMARY));
            addRenderableWidget(new ModernInnButton(x + 16 + (buttonW + gap) * 2, controlsY, buttonW, 22, Component.literal("Ajouter: starter"),
                    button -> addSelected(InnMenuEntry.Type.STARTER), ModernInnButton.Style.PRIMARY));
        }

        int footerGap = 5;
        int footerW = (panelW - 32 - footerGap * 3) / 4;
        addRenderableWidget(new ModernInnButton(x + 16, y + panelH - 28, footerW, 20, Component.literal("Precedent"),
                button -> changePage(-1), ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(x + 16 + footerW + footerGap, y + panelH - 28, footerW, 20, Component.literal("Suivant"),
                button -> changePage(1), ModernInnButton.Style.SECONDARY));
        addRenderableWidget(new ModernInnButton(x + 16 + (footerW + footerGap) * 2, y + panelH - 28, footerW, 20, Component.literal("Actualiser"),
                button -> refreshReferences(), ModernInnButton.Style.PRIMARY));
        addRenderableWidget(new ModernInnButton(x + 16 + (footerW + footerGap) * 3, y + panelH - 28, footerW, 20, Component.literal("Retour"),
                button -> Minecraft.getInstance().setScreen(parent), ModernInnButton.Style.SECONDARY));

        if (!requested) {
            requested = true;
            PacketHandler.INSTANCE.sendToServer(new PacketRequestInnReferences());
        }
        setInitialFocus(search);
    }

    private void addSelected(InnMenuEntry.Type type) {
        InnReferenceItem selected = selectedReference();
        if (inn == null || selected == null || !inn.hasCustomerRoute() || selected.referencePrice() <= 0L
                || !itemStack(selected.itemId()).isEdible()) return;
        PacketHandler.INSTANCE.sendToServer(new PacketInnMenuAction(inn.getName(), PacketInnMenuAction.ADD_REFERENCE,
                selected.itemId(), type.name()));
    }

    private void changePage(int direction) {
        int rows = visibleRows();
        int maxOffset = Math.max(0, filteredReferences().size() - rows);
        offset = Math.max(0, Math.min(maxOffset, offset + direction * rows));
        selectedIndex = -1;
    }

    private InnReferenceItem selectedReference() {
        List<InnReferenceItem> filtered = filteredReferences();
        return selectedIndex >= 0 && selectedIndex < filtered.size() ? filtered.get(selectedIndex) : null;
    }

    private List<InnReferenceItem> filteredReferences() {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return references.stream()
                .filter(reference -> switch (filter) {
                    case REFERENCED -> reference.referencePrice() > 0L;
                    case MISSING -> reference.referencePrice() <= 0L;
                    case FOOD -> itemStack(reference.itemId()).isEdible();
                    default -> true;
                })
                .filter(reference -> normalized.isEmpty()
                        || safe(reference.displayName()).toLowerCase(Locale.ROOT).contains(normalized)
                        || safe(reference.itemId()).toLowerCase(Locale.ROOT).contains(normalized)
                        || safe(reference.category()).toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelW = panelWidth();
        int panelH = panelHeight();
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;

        graphics.fill(x - 2, y - 2, x + panelW + 2, y + panelH + 2, 0xAA000000);
        graphics.fillGradient(x, y, x + panelW, y + panelH, 0xF01F1B17, 0xF0111517);
        graphics.fill(x, y, x + panelW, y + 36, 0xFF2D241B);
        graphics.fill(x, y + 36, x + panelW, y + 37, 0xFF6B4D25);
        graphics.drawString(font, "Items utilises dans les trades", x + 16, y + 14, GOLD, false);

        List<InnReferenceItem> filtered = filteredReferences();
        int rows = visibleRows();
        offset = Math.max(0, Math.min(offset, Math.max(0, filtered.size() - rows)));
        int listY = y + 101;
        for (int row = 0; row < Math.min(rows, filtered.size() - offset); row++) {
            int index = offset + row;
            InnReferenceItem reference = filtered.get(index);
            int rowY = listY + row * 22;
            graphics.fill(x + 16, rowY - 2, x + panelW - 16, rowY + 18,
                    index == selectedIndex ? 0x663C6E71 : 0x33100000);
            ItemStack stack = itemStack(reference.itemId());
            if (!stack.isEmpty()) graphics.renderItem(stack, x + 20, rowY);
            graphics.drawString(font, fit(reference.displayName(), panelW / 3), x + 42, rowY, TEXT, false);
            graphics.drawString(font, fit(reference.category(), panelW / 5), x + panelW / 2, rowY, MUTED, false);
            boolean defined = reference.referencePrice() > 0L;
            String price = defined ? "Defini: " + MedievalCoinsIntegration.formatMoney(reference.referencePrice()) : "Sans reference";
            graphics.drawString(font, price, x + panelW - 22 - font.width(price), rowY, defined ? GREEN : RED, false);
        }

        if (references.isEmpty()) {
            graphics.drawCenteredString(font, "Chargement des items de trade...", x + panelW / 2, listY + 8, MUTED);
        } else if (filtered.isEmpty()) {
            graphics.drawCenteredString(font, "Aucun item de trade ne correspond a la recherche.", x + panelW / 2, listY + 8, MUTED);
        }
        long definedCount = filtered.stream().filter(reference -> reference.referencePrice() > 0L).count();
        graphics.drawCenteredString(font, filtered.size() + " items de trade | " + definedCount + " avec reference",
                x + panelW / 2, y + panelH - 73, GREEN);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = panelWidth();
        int panelH = panelHeight();
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;
        List<InnReferenceItem> filtered = filteredReferences();
        int listY = y + 101;
        for (int row = 0; row < Math.min(visibleRows(), filtered.size() - offset); row++) {
            int rowY = listY + row * 22;
            if (mouseX >= x + 16 && mouseX <= x + panelW - 16 && mouseY >= rowY - 2 && mouseY <= rowY + 18) {
                selectedIndex = offset + row;
                InnReferenceItem selected = filtered.get(selectedIndex);
                if (selected.referencePrice() <= 0L && inn == null) {
                    Minecraft.getInstance().setScreen(new InnReferenceCreationScreen(selected, this));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        changePage(delta > 0 ? -1 : 1);
        return true;
    }

    private int panelWidth() {
        return Math.max(300, Math.min(620, width - 24));
    }

    private int panelHeight() {
        return Math.max(210, Math.min(360, height - 24));
    }

    private int visibleRows() {
        return Math.max(1, Math.min(9, (panelHeight() - 180) / 22));
    }

    private ItemStack itemStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return ItemStack.EMPTY;
        return new ItemStack(ForgeRegistries.ITEMS.getValue(id));
    }

    private String fit(String value, int maxWidth) {
        String text = safe(value);
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 3 && font.width(text + "...") > maxWidth) text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
