package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketInnRouteAction;
import fr.renblood.medievalstructures.network.PacketRequestInnRoutes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public class InnRouteSelectionScreen extends Screen {
    private final Inn inn;
    private final InnOwnerScreen parent;
    private List<String> routes = List.of();
    private EditBox search;
    private String query = "";
    private int selected = -1;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROUTES = 6;

    public InnRouteSelectionScreen(Inn inn, InnOwnerScreen parent) {
        super(Component.literal("Route clients"));
        this.inn = inn;
        this.parent = parent;
    }

    public void updateRoutes(String innName, List<String> routes) {
        if (inn.getName().equals(innName)) this.routes = List.copyOf(routes);
    }

    @Override
    protected void init() {
        int w = Math.max(310, Math.min(600, width - 32));
        int x = (width - w) / 2;
        int y = Math.max(12, (height - 300) / 2);
        search = new EditBox(font, x + 18, y + 48, w - 36, 20, Component.literal("Rechercher"));
        search.setHint(Component.literal("Nom ou commande d'itin\u00e9raire..."));
        search.setValue(query);
        search.setResponder(value -> { query = value; selected = -1; scrollOffset = 0; });
        addRenderableWidget(search);
        addRenderableWidget(new ModernInnButton(x + 18, y + 254, (w - 48) / 3, 22, Component.literal("Associer"),
                button -> link(), ModernInnButton.Style.SUCCESS));
        addRenderableWidget(new ModernInnButton(x + 24 + (w - 48) / 3, y + 254, (w - 48) / 3, 22, Component.literal("Retirer"),
                button -> clear(), ModernInnButton.Style.DANGER));
        addRenderableWidget(new ModernInnButton(x + 30 + 2 * ((w - 48) / 3), y + 254, (w - 48) / 3, 22, Component.literal("Retour"),
                button -> Minecraft.getInstance().setScreen(parent), ModernInnButton.Style.SECONDARY));
        PacketHandler.INSTANCE.sendToServer(new PacketRequestInnRoutes(inn.getName()));
    }

    private List<String> filtered() {
        String value = query.trim().toLowerCase(Locale.ROOT);
        return routes.stream().filter(route -> value.isEmpty() || route.toLowerCase(Locale.ROOT).contains(value)).toList();
    }

    private void link() {
        List<String> filtered = filtered();
        if (selected < 0 || selected >= filtered.size()) return;
        String selector = filtered.get(selected).split("\\|", 2)[0].trim();
        PacketHandler.INSTANCE.sendToServer(new PacketInnRouteAction(inn.getName(), selector));
        Minecraft.getInstance().setScreen(parent);
    }

    private void clear() {
        PacketHandler.INSTANCE.sendToServer(new PacketInnRouteAction(inn.getName(), ""));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int w = Math.max(310, Math.min(600, width - 32));
        int x = (width - w) / 2;
        int y = Math.max(12, (height - 300) / 2);
        List<String> filtered = filtered();
        int visible = Math.min(VISIBLE_ROUTES, Math.max(0, filtered.size() - scrollOffset));
        for (int row = 0; row < visible; row++) {
            int rowY = y + 82 + row * 23;
            if (mouseX >= x + 18 && mouseX <= x + w - 18 && mouseY >= rowY && mouseY <= rowY + 19) {
                selected = scrollOffset + row;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxOffset = Math.max(0, filtered().size() - VISIBLE_ROUTES);
        if (maxOffset == 0) return super.mouseScrolled(mouseX, mouseY, delta);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + (delta < 0 ? 1 : -1)));
        selected = -1;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        List<String> filtered = filtered();
        if (filtered.isEmpty()) return super.keyPressed(key, scanCode, modifiers);
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            int direction = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
            selected = selected < 0
                    ? (direction > 0 ? scrollOffset : Math.min(filtered.size() - 1, scrollOffset + VISIBLE_ROUTES - 1))
                    : Math.max(0, Math.min(filtered.size() - 1, selected + direction));
            if (selected < scrollOffset) scrollOffset = selected;
            if (selected >= scrollOffset + VISIBLE_ROUTES) scrollOffset = selected - VISIBLE_ROUTES + 1;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && selected >= 0) {
            link();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int w = Math.max(310, Math.min(600, width - 32));
        int x = (width - w) / 2;
        int y = Math.max(12, (height - 300) / 2);
        graphics.fill(x - 2, y - 2, x + w + 2, y + 294, 0xAA000000);
        graphics.fillGradient(x, y, x + w, y + 292, 0xF01F1B17, 0xF0111517);
        graphics.fill(x, y, x + w, y + 36, 0xFF2D241B);
        graphics.drawString(font, "Route clients de " + inn.getName(), x + 18, y + 14, 0xFFFFC86B, false);
        List<String> filtered = filtered();
        int visible = Math.min(VISIBLE_ROUTES, Math.max(0, filtered.size() - scrollOffset));
        for (int row = 0; row < visible; row++) {
            int routeIndex = scrollOffset + row;
            int rowY = y + 82 + row * 23;
            boolean hovered = mouseX >= x + 18 && mouseX <= x + w - 18 && mouseY >= rowY && mouseY <= rowY + 19;
            int color = routeIndex == selected ? 0xAA466C68 : hovered ? 0x66584936 : 0x332B2722;
            graphics.fill(x + 18, rowY, x + w - 18, rowY + 19, color);
            graphics.drawString(font, fit(filtered.get(routeIndex), w - 48), x + 26, rowY + 6, 0xFFE8E0CC, false);
        }
        if (filtered.size() > VISIBLE_ROUTES) {
            graphics.drawString(font, (scrollOffset + 1) + "-" + Math.min(filtered.size(), scrollOffset + VISIBLE_ROUTES) + " / " + filtered.size(),
                    x + w - 72, y + 70, 0xFFBBAF96, false);
        }
        if (filtered.isEmpty()) graphics.drawCenteredString(font, "Aucun itin\u00e9raire disponible.", x + w / 2, y + 110, 0xFFBBAF96);
        else if (filtered.size() > VISIBLE_ROUTES) graphics.drawString(font, "Molette ou fl\u00e8ches : defiler", x + 18, y + 70, 0xFFBBAF96, false);
        graphics.drawString(font, "Actuelle: " + (inn.hasCustomerRoute() ? inn.getCustomerRouteId() : "aucune"), x + 18, y + 238, 0xFFBBAF96, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String fit(String value, int maxWidth) {
        String text = value;
        while (text.length() > 3 && font.width(text + "...") > maxWidth) text = text.substring(0, text.length() - 1);
        return text.equals(value) ? text : text + "...";
    }
}
