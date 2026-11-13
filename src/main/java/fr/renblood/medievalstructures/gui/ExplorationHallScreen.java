package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.network.*;
import fr.renblood.medievalstructures.util.ExplorationCostCalculator;
import fr.renblood.medievalstructures.util.ExplorationProgression;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExplorationHallScreen extends AbstractContainerScreen<ExplorationHallMenu> {
    private static final int PANEL_MIN_WIDTH = 460;
    private static final int PANEL_MAX_WIDTH = 520;
    private static final int PANEL_MIN_HEIGHT = 300;
    private static final int PANEL_MAX_HEIGHT = 360;
    private static final int TEXT = 0xFFE8E0CC;
    private static final int MUTED_TEXT = 0xFFBBAF96;
    private static final int GOLD = 0xFFFFC86B;
    private static final int GREEN = 0xFF73D17C;
    private static final int RED = 0xFFFF6B6B;
    private static final int AMBER = 0xFFFFB14A;

    private enum ScreenState {
        MAIN_MENU,
        CREATE_EXPLORATION,
        VIEW_EXPLORATION,
        TALENT_TREE
    }

    private ScreenState currentState = ScreenState.MAIN_MENU;
    private int selectedDuration = 20;
    private final List<PacketExplorationAction.PlayerConfig> playerConfigs = new ArrayList<>();
    private int currentPlayerIndex = 0;
    private int viewPlayerIndex = 0;

    private EditBox playerNameBox;
    private Button newExplorationButton;
    private Button launchButton;
    private Button viewButton;
    private Button cancelButton;
    private Button talentsButton;
    private Button durationMinusButton;
    private Button durationPlusButton;
    private Button addPlayerButton;
    private Button removePlayerButton;
    private Button prevPlayerButton;
    private Button nextPlayerButton;
    private Button chestMinusButton;
    private Button chestPlusButton;
    private Button animalMinusButton;
    private Button animalPlusButton;
    private Button saveButton;
    private Button backButton;
    private Button viewBackButton;
    private Button talentBackButton;
    private Button viewPrevPlayerButton;
    private Button viewNextPlayerButton;
    private Button resendInviteButton;
    private Button removePlayerViewButton;

    private fr.renblood.medievalstructures.util.ExplorationViewport viewport;
    private String departurePhase="";
    private int departureSeconds,departureProgress,refreshTicks;
    private boolean canCancelDeparture,showHelp;
    private long cancelConfirmation,lastAction;
    private Button helpButton;
    public void updateDeparture(String phase,int seconds,int progress,boolean canCancel){
        departurePhase=phase;departureSeconds=seconds;departureProgress=progress;canCancelDeparture=canCancel;updateLaunchState();
    }
    private long lastInviteTime = 0;
    private long lastHighlightRequest = 0;
    private boolean isDebugMode = false;

    public ExplorationHallScreen(ExplorationHallMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 380;
        this.imageHeight = 246;
        String playerName = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : "";
        java.util.UUID playerUuid = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        playerConfigs.add(new PacketExplorationAction.PlayerConfig(1, 0, playerName, playerUuid, PacketExplorationAction.InvitationStatus.ACCEPTED));
        this.isDebugMode = menu.isDebugMode();
    }

    @Override
    protected void init() {
        viewport=fr.renblood.medievalstructures.util.ExplorationViewport.fit(this.width,this.height);
        this.width=viewport.width();this.height=viewport.height();
        this.imageWidth=560;this.imageHeight=380;
        super.init();
        initWidgets();
        updateVisibility();
        requestHighlight();
        lastHighlightRequest=System.currentTimeMillis();
        requestState();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (playerNameBox != null && playerNameBox.isFocused()) {
            if (keyCode == 256) {
                playerNameBox.setFocused(false);
                return true;
            }
            if (playerNameBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void updateExplorationState(boolean hasExploration, boolean isDebug) {
        this.isDebugMode = isDebug;
        if (this.menu != null) this.menu.setDebugMode(isDebug);
        if (launchButton != null) launchButton.active = hasExploration;
        if (viewButton != null) viewButton.active = hasExploration;
        updateLaunchState();
    }

    private void initWidgets() {
        int x = leftPos;
        int y = topPos;
        int contentX = x + 18;
        int contentW = imageWidth - 36;
        int centerX = x + imageWidth / 2;
        int footerY = y + imageHeight - 34;
        int actionW = Math.min(202, contentW / 2 - 8);

        newExplorationButton = addRenderableWidget(new StyledButton(contentX, y + 62, actionW, 24,
                Component.literal("Nouvelle exploration"), b -> switchState(ScreenState.CREATE_EXPLORATION), ButtonStyle.PRIMARY));
        launchButton = addRenderableWidget(new StyledButton(contentX, y + 92, actionW, 24,
                Component.translatable("gui.medieval_structures.exploration.launch"), this::onLaunch, ButtonStyle.SUCCESS));
        viewButton = addRenderableWidget(new StyledButton(contentX, y + 122, actionW, 24,
                Component.literal("Voir l’équipe"), b -> switchState(ScreenState.VIEW_EXPLORATION), ButtonStyle.SECONDARY));
        talentsButton = addRenderableWidget(new StyledButton(contentX, y + 152, actionW, 24,
                Component.literal("Arbre des talents"), b -> switchState(ScreenState.TALENT_TREE), ButtonStyle.PRIMARY));
        cancelButton = addRenderableWidget(new StyledButton(contentX, y + 182, actionW, 22,
                Component.literal("Annuler le lancement"), this::onCancel, ButtonStyle.DANGER));

        helpButton=addRenderableWidget(new StyledButton(contentX,footerY,actionW,22,Component.literal("Aide au départ"),b->showHelp=!showHelp,ButtonStyle.SECONDARY));
        int rowY = y + 70;
        durationMinusButton = addRenderableWidget(new StyledButton(contentX, rowY, 28, 22, Component.literal("-"), b -> changeDuration(-1), ButtonStyle.SECONDARY));
        durationPlusButton = addRenderableWidget(new StyledButton(contentX + 148, rowY, 28, 22, Component.literal("+"), b -> changeDuration(1), ButtonStyle.SECONDARY));

        playerNameBox = new EditBox(this.font, contentX, y + 123, Math.min(180, contentW / 2 - 36), 22, Component.literal("Pseudo"));
        playerNameBox.setMaxLength(32);
        playerNameBox.setBordered(true);
        addRenderableWidget(playerNameBox);
        addPlayerButton = addRenderableWidget(new StyledButton(playerNameBox.getX() + playerNameBox.getWidth() + 8, y + 123, 66, 22,
                Component.literal("Ajouter"), b -> addPlayerFromInput(), ButtonStyle.SUCCESS));

        int configX = centerX + 10;
        int configW = Math.max(132, x + imageWidth - 18 - configX);
        prevPlayerButton = addRenderableWidget(new StyledButton(configX, y + 90, 28, 22, Component.literal("<"), b -> selectCreationPlayer(-1), ButtonStyle.SECONDARY));
        nextPlayerButton = addRenderableWidget(new StyledButton(configX + configW - 28, y + 90, 28, 22, Component.literal(">"), b -> selectCreationPlayer(1), ButtonStyle.SECONDARY));
        removePlayerButton = addRenderableWidget(new StyledButton(configX, y + 123, configW, 22, Component.literal("Retirer ce joueur"), b -> removeCurrentPlayer(), ButtonStyle.DANGER));
        chestMinusButton = addRenderableWidget(new StyledButton(configX, y + 172, 28, 22, Component.literal("-"), b -> changeChests(-1), ButtonStyle.SECONDARY));
        chestPlusButton = addRenderableWidget(new StyledButton(configX + configW - 28, y + 172, 28, 22, Component.literal("+"), b -> changeChests(1), ButtonStyle.SECONDARY));
        animalMinusButton = addRenderableWidget(new StyledButton(configX, y + 214, 28, 22, Component.literal("-"), b -> changeAnimals(-1), ButtonStyle.SECONDARY));
        animalPlusButton = addRenderableWidget(new StyledButton(configX + configW - 28, y + 214, 28, 22, Component.literal("+"), b -> changeAnimals(1), ButtonStyle.SECONDARY));

        saveButton = addRenderableWidget(new StyledButton(x + imageWidth - 138, footerY, 120, 24,
                Component.literal("Enregistrer"), this::onSave, ButtonStyle.SUCCESS));
        backButton = addRenderableWidget(new StyledButton(contentX, footerY, 82, 24,
                Component.translatable("gui.back"), b -> switchState(ScreenState.MAIN_MENU), ButtonStyle.SECONDARY));

        viewPrevPlayerButton = addRenderableWidget(new StyledButton(centerX + 10, y + 122, 28, 22, Component.literal("<"), b -> selectViewPlayer(-1), ButtonStyle.SECONDARY));
        viewNextPlayerButton = addRenderableWidget(new StyledButton(x + imageWidth - 46, y + 122, 28, 22, Component.literal(">"), b -> selectViewPlayer(1), ButtonStyle.SECONDARY));
        resendInviteButton = addRenderableWidget(new StyledButton(x + imageWidth - 178, y + 232, 160, 22, Component.literal("Relancer invitation"), this::onResendInvite, ButtonStyle.PRIMARY));
        removePlayerViewButton = addRenderableWidget(new StyledButton(x + imageWidth - 178, y + 260, 160, 22, Component.literal("Retirer joueur"), this::onRemovePlayerView, ButtonStyle.DANGER));
        viewBackButton = addRenderableWidget(new StyledButton(contentX, footerY, 82, 24,
                Component.translatable("gui.back"), b -> switchState(ScreenState.MAIN_MENU), ButtonStyle.SECONDARY));
        talentBackButton = addRenderableWidget(new StyledButton(contentX, footerY, 82, 24,
                Component.translatable("gui.back"), b -> switchState(ScreenState.MAIN_MENU), ButtonStyle.SECONDARY));
    }

    private void requestHighlight() {
        if (this.menu.getPos() != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestChestHighlight(this.menu.getPos()));
        }
    }

    private void requestState() {
        if (this.menu.getPos() != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestExplorationState(this.menu.getPos()));
        }
    }

    private void switchState(ScreenState state) {
        currentState = state;
        if (state == ScreenState.VIEW_EXPLORATION) viewPlayerIndex = 0;
        updateVisibility();
    }

    private void updateVisibility() {
        boolean main = currentState == ScreenState.MAIN_MENU;
        boolean create = currentState == ScreenState.CREATE_EXPLORATION;
        boolean view = currentState == ScreenState.VIEW_EXPLORATION;
        boolean talents = currentState == ScreenState.TALENT_TREE;

        setVisible(newExplorationButton, main);
        setVisible(helpButton,main);
        setVisible(launchButton, main);
        setVisible(viewButton, main);
        setVisible(cancelButton, main);
        setVisible(talentsButton, main);
        setVisible(durationMinusButton, create);
        setVisible(durationPlusButton, create);
        if (playerNameBox != null) playerNameBox.visible = create;
        setVisible(addPlayerButton, create);
        setVisible(removePlayerButton, create);
        setVisible(prevPlayerButton, create);
        setVisible(nextPlayerButton, create);
        setVisible(chestMinusButton, create);
        setVisible(chestPlusButton, create);
        setVisible(animalMinusButton, create);
        setVisible(animalPlusButton, create);
        setVisible(saveButton, create);
        setVisible(backButton, create);
        setVisible(viewPrevPlayerButton, view);
        setVisible(viewNextPlayerButton, view);
        setVisible(resendInviteButton, view);
        setVisible(removePlayerViewButton, view);
        setVisible(viewBackButton, view);
        setVisible(talentBackButton, talents);

        if (create) updateCreationWidgets();
        if (view) updateViewWidgets();
        if (main) updateLaunchState();
    }

    private void setVisible(Button button, boolean visible) {
        if (button != null) button.visible = visible;
    }

    private void updateCreationWidgets() {
        if (playerConfigs.isEmpty()) return;
        clampSelectedDuration();
        currentPlayerIndex = Math.max(0, Math.min(currentPlayerIndex, playerConfigs.size() - 1));
        if (removePlayerButton != null) removePlayerButton.active = currentPlayerIndex > 0;
        if (prevPlayerButton != null) prevPlayerButton.active = currentPlayerIndex > 0;
        if (nextPlayerButton != null) nextPlayerButton.active = currentPlayerIndex < playerConfigs.size() - 1;

        PacketExplorationAction.PlayerConfig config = playerConfigs.get(currentPlayerIndex);
        int maxChests = getClientMaxChests();
        if (config.chestCount > maxChests) config.chestCount = maxChests;
        if (config.animalCount > ExplorationProgression.MAX_ANIMALS_PER_PLAYER) config.animalCount = ExplorationProgression.MAX_ANIMALS_PER_PLAYER;
        if (chestMinusButton != null) chestMinusButton.active = config.chestCount > 1;
        if (chestPlusButton != null) chestPlusButton.active = config.chestCount < maxChests;
        if (animalMinusButton != null) animalMinusButton.active = config.animalCount > 0;
        if (animalPlusButton != null) animalPlusButton.active = config.animalCount < ExplorationProgression.MAX_ANIMALS_PER_PLAYER;
    }

    private void updateViewWidgets() {
        ExplorationHallBlockEntity be = getBlockEntity();
        boolean hasConfig = be != null && be.hasExplorationReady() && !be.getSavedConfigs().isEmpty();
        if (!hasConfig) {setVisible(resendInviteButton,false);setVisible(removePlayerViewButton,false);return;}

        viewPlayerIndex = Math.max(0, Math.min(viewPlayerIndex, be.getSavedConfigs().size() - 1));
        PacketExplorationAction.PlayerConfig config = be.getSavedConfigs().get(viewPlayerIndex);
        if (viewPrevPlayerButton != null) viewPrevPlayerButton.active = viewPlayerIndex > 0;
        if (viewNextPlayerButton != null) viewNextPlayerButton.active = viewPlayerIndex < be.getSavedConfigs().size() - 1;
        if (resendInviteButton != null) {
            resendInviteButton.visible = config.status == PacketExplorationAction.InvitationStatus.PENDING && viewPlayerIndex > 0;
            resendInviteButton.active = System.currentTimeMillis() - lastInviteTime > 15000;
        }
        if (removePlayerViewButton != null) removePlayerViewButton.visible = viewPlayerIndex > 0;
    }

    private void updateLaunchState() {
        ExplorationHallBlockEntity be = getBlockEntity();
        boolean hasExplo = be != null && be.hasExplorationReady();
        boolean busy=!departurePhase.isEmpty();
        boolean coolingDown=System.currentTimeMillis()-lastAction<1200;
        if(newExplorationButton!=null)newExplorationButton.active=!busy&&!coolingDown;
        if (viewButton != null) viewButton.active = hasExplo&&!busy;
        if (cancelButton != null) {
            cancelButton.active=(hasExplo||canCancelDeparture)&&!coolingDown;
            cancelButton.setMessage(Component.literal(cancelConfirmation>System.currentTimeMillis()?"Confirmer l’annulation":canCancelDeparture?"Annuler / remboursement":"Effacer la préparation"));
        }
        if (launchButton == null) return;

        launchButton.active = hasExplo&&!busy&&!coolingDown;
        launchButton.setMessage(Component.translatable("gui.medieval_structures.exploration.launch"));
        if (hasExplo&&!busy&&!coolingDown) {
            boolean allAccepted = be.getSavedConfigs().stream()
                    .allMatch(c -> c.status == PacketExplorationAction.InvitationStatus.ACCEPTED);
            if (isDebugMode) {
                launchButton.active = true;
                launchButton.setMessage(Component.literal("Lancer maintenant (DEBUG)"));
            } else if (!allAccepted) {
                launchButton.active = false;
                launchButton.setMessage(Component.literal("En attente des joueurs"));
            }
        }
    }

    private void addPlayerFromInput() {
        String name = playerNameBox.getValue().trim();
        if (name.isEmpty()||playerConfigs.size()>=10) return;
        boolean exists = playerConfigs.stream().anyMatch(c -> c.playerName != null && c.playerName.equalsIgnoreCase(name));
        if (!exists) {
            playerConfigs.add(new PacketExplorationAction.PlayerConfig(1, 0, name));
            playerNameBox.setValue("");
            currentPlayerIndex = playerConfigs.size() - 1;
            updateCreationWidgets();
        }
    }

    private void removeCurrentPlayer() {
        if (playerConfigs.size() > 1 && currentPlayerIndex > 0) {
            playerConfigs.remove(currentPlayerIndex);
            currentPlayerIndex = Math.min(currentPlayerIndex, playerConfigs.size() - 1);
            updateCreationWidgets();
        }
    }

    private void selectCreationPlayer(int delta) {
        currentPlayerIndex = Math.max(0, Math.min(currentPlayerIndex + delta, playerConfigs.size() - 1));
        updateCreationWidgets();
    }

    private void selectViewPlayer(int delta) {
        ExplorationHallBlockEntity be = getBlockEntity();
        if (be == null || be.getSavedConfigs().isEmpty()) return;
        viewPlayerIndex = Math.max(0, Math.min(viewPlayerIndex + delta, be.getSavedConfigs().size() - 1));
        updateViewWidgets();
    }

    private void changeDuration(int delta) {
        List<Integer> durations = getClientAvailableDurations();
        int index = durations.indexOf(selectedDuration);
        if (index < 0) index = 0;
        selectedDuration = durations.get(Math.max(0, Math.min(index + delta, durations.size() - 1)));
        updateCreationWidgets();
    }

    private void changeChests(int delta) {
        if (playerConfigs.isEmpty()) return;
        PacketExplorationAction.PlayerConfig config = playerConfigs.get(currentPlayerIndex);
        config.chestCount = Math.max(1, Math.min(getClientMaxChests(), config.chestCount + delta));
        updateCreationWidgets();
    }

    private void changeAnimals(int delta) {
        if (playerConfigs.isEmpty()) return;
        PacketExplorationAction.PlayerConfig config = playerConfigs.get(currentPlayerIndex);
        config.animalCount = Math.max(0, Math.min(ExplorationProgression.MAX_ANIMALS_PER_PLAYER, config.animalCount + delta));
        updateCreationWidgets();
    }

    private void clampSelectedDuration() {
        List<Integer> durations = getClientAvailableDurations();
        if (!durations.contains(selectedDuration)) {
            selectedDuration = durations.get(durations.size() - 1);
        }
    }

    private List<Integer> getClientAvailableDurations() {
        return ExplorationProgression.getAvailableDurations(getClientHallLevel(), Minecraft.getInstance().player);
    }

    private int getClientMaxChests() {
        return ExplorationProgression.getMaxChests(getClientHallLevel(), Minecraft.getInstance().player);
    }

    private int getClientMaxDuration() {
        List<Integer> durations = getClientAvailableDurations();
        return durations.get(durations.size() - 1);
    }

    private int getClientHallLevel() {
        ExplorationHallBlockEntity be = getBlockEntity();
        return be != null ? be.getHallLevel() : 1;
    }

    private int getClientCompletedExplorations() {
        ExplorationHallBlockEntity be = getBlockEntity();
        UUID uuid = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        return be != null ? be.getCompletedExplorations(uuid) : 0;
    }

    private String getTalentSummary() {
        if (Minecraft.getInstance().player == null) return "Talents: cache indisponible";
        List<Integer> talents = MedievalCoinsIntegration.getUnlockedExplorerTalents(Minecraft.getInstance().player);
        int slots = Math.max(10, MedievalCoinsIntegration.getExplorerTalentSlots(Minecraft.getInstance().player));
        if (slots == 0) return "Talents: aucune donnee";
        if (talents.isEmpty()) return "Talents: 0/" + slots;

        StringBuilder sb = new StringBuilder("Talents: ").append(talents.size()).append("/").append(slots).append("  [");
        for (int i = 0; i < talents.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(talents.get(i));
        }
        return sb.append("]").toString();
    }

    private String getCurrentStatsLine() {
        List<Integer> durations = getClientAvailableDurations();
        int maxDuration = durations.get(durations.size() - 1);
        return "Stats: " + maxDuration + " min  " + getClientMaxChests() + " coffres/j  " + ExplorationProgression.MAX_ANIMALS_PER_PLAYER + " animaux/j";
    }

    private boolean hasTalent(int index) {
        return Minecraft.getInstance().player != null && MedievalCoinsIntegration.hasExplorerTalent(Minecraft.getInstance().player, index);
    }

    private int getAvailableTalentPoints() {
        if (Minecraft.getInstance().player == null) return 0;
        int level = Math.min(10, MedievalCoinsIntegration.getExplorerLevel(Minecraft.getInstance().player));
        int unlocked = MedievalCoinsIntegration.getUnlockedExplorerTalents(Minecraft.getInstance().player).size();
        return Math.max(0, level - unlocked);
    }

    private void onSave(Button button) {
        BlockPos pos = this.menu.getPos();
        if (pos != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketSaveExploration(pos, selectedDuration, playerConfigs));
            switchState(ScreenState.MAIN_MENU);
        }
    }

    private void onLaunch(Button button) {
        BlockPos pos = this.menu.getPos();
        if (pos != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketLaunchExploration(pos));
            lastAction=System.currentTimeMillis();requestState();updateLaunchState();
        }
    }

    private void onCancel(Button button) {
        BlockPos pos = this.menu.getPos();
        if (pos != null) {
            if(cancelConfirmation<System.currentTimeMillis()){cancelConfirmation=System.currentTimeMillis()+4000;updateLaunchState();return;}
            cancelConfirmation=0;
            PacketHandler.INSTANCE.sendToServer(new PacketCancelExploration(pos));
            lastAction=System.currentTimeMillis();requestState();updateLaunchState();
        }
    }

    private void onResendInvite(Button button) {
        BlockPos pos = this.menu.getPos();
        if (pos != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketResendInvitation(pos, viewPlayerIndex));
            lastInviteTime = System.currentTimeMillis();
            updateViewWidgets();
        }
    }

    private void onRemovePlayerView(Button button) {
        BlockPos pos = this.menu.getPos();
        if (pos != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketRemovePlayer(pos, viewPlayerIndex));
            if (viewPlayerIndex > 0) viewPlayerIndex--;
            updateViewWidgets();
        }
    }

    private ExplorationHallBlockEntity getBlockEntity() {
        BlockPos pos = this.menu.getPos();
        if (pos != null && this.minecraft != null && this.minecraft.level != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(pos);
            if (be instanceof ExplorationHallBlockEntity hall) return hall;
        }
        return null;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xAA000000);
        guiGraphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFC181B1E, 0xFC101315);
        guiGraphics.fill(x, y, x + imageWidth, y + 34, 0xFF202427);
        guiGraphics.fill(x, y + 34, x + imageWidth, y + 35, 0xFF8D7447);
        guiGraphics.fill(x + 10, y + 42, x + imageWidth - 10, y + imageHeight - 44, 0x402B3034);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.pose().pushPose();guiGraphics.pose().scale(viewport.scale(),viewport.scale(),1);
        mouseX=(int)viewport.logical(mouseX);mouseY=(int)viewport.logical(mouseY);
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderContent(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
        guiGraphics.pose().popPose();
    }

    @Override public void containerTick(){
        super.containerTick();updateLaunchState();
        if(++refreshTicks%20==0)requestState();
        if((currentState==ScreenState.CREATE_EXPLORATION||currentState==ScreenState.VIEW_EXPLORATION)&&System.currentTimeMillis()-lastHighlightRequest>5000){requestHighlight();lastHighlightRequest=System.currentTimeMillis();}
    }
    @Override public boolean mouseClicked(double x,double y,int button){return super.mouseClicked(viewport.logical(x),viewport.logical(y),button);}
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(viewport.logical(x),viewport.logical(y),button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return super.mouseDragged(viewport.logical(x),viewport.logical(y),button,viewport.logical(dx),viewport.logical(dy));}
    @Override public boolean mouseScrolled(double x,double y,double delta){return super.mouseScrolled(viewport.logical(x),viewport.logical(y),delta);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(viewport.logical(x),viewport.logical(y));}

    private void renderContent(GuiGraphics guiGraphics) {
        int x = leftPos;
        int y = topPos;
        int contentX = x + 18;
        int centerX = x + imageWidth / 2;

        drawTitle(guiGraphics, "Hall d'exploration");
        if (currentState == ScreenState.MAIN_MENU) {
            renderMain(guiGraphics, contentX, y);
        } else if (currentState == ScreenState.CREATE_EXPLORATION) {
            renderCreation(guiGraphics, contentX, centerX, y);
        } else if (currentState == ScreenState.VIEW_EXPLORATION) {
            renderView(guiGraphics, contentX, centerX, y);
        } else {
            renderTalentTree(guiGraphics, contentX, y);
        }
    }

    private void drawTitle(GuiGraphics guiGraphics, String text) {
        guiGraphics.drawString(this.font, text, leftPos + 18, topPos + 13, GOLD, false);
    }

    private void renderMain(GuiGraphics guiGraphics, int contentX, int y) {
        ExplorationHallBlockEntity be = getBlockEntity();
        boolean hasExplo = be != null && be.hasExplorationReady();
        int hallLevel = be != null ? be.getHallLevel() : 1;
        int completed = be != null ? be.getCompletedExplorations() : 0;
        int infoX = contentX + Math.min(220, (imageWidth - 36) / 2);
        guiGraphics.drawString(this.font,!departurePhase.isEmpty()?"Départ suivi depuis le hall":hasExplo?"Équipe prête à partir":"Préparer une expédition",contentX,y+42,TEXT,false);
        if(!departurePhase.isEmpty()){
            guiGraphics.drawString(font,font.plainSubstrByWidth(departurePhase,220),contentX,y+220,GOLD,false);
            guiGraphics.fill(contentX,y+238,contentX+210,y+241,0xFF353B40);
            guiGraphics.fill(contentX,y+238,contentX+210*departureProgress/100,y+241,GOLD);
            guiGraphics.drawString(font,departureSeconds>0?String.format("%02d:%02d",departureSeconds/60,departureSeconds%60):"Le serveur prépare la suite…",contentX,y+250,MUTED_TEXT,false);
        }
        if(showHelp){
            String[] help={"1. Configure les participants et leurs coffres.","2. Fais accepter les invitations, puis rassemble l’équipe.","3. Après le départ : préparation sans bloquer le serveur.","Retour automatique ; un coffre bloqué reste récupérable."};
            for(int i=0;i<help.length;i++)guiGraphics.drawString(font,help[i],contentX,y+284+i*12,MUTED_TEXT,false);
        }
        guiGraphics.drawString(this.font, "Hall et progression", infoX, y + 62, GOLD, false);
        guiGraphics.drawString(this.font, "Niveau: " + hallLevel, infoX, y + 82, TEXT, false);
        guiGraphics.drawString(this.font, "Total hall: " + completed, infoX, y + 96, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Vous: " + getClientCompletedExplorations(), infoX, y + 110, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Stats actuelles", infoX, y + 136, GOLD, false);
        guiGraphics.drawString(this.font, "Durée maximale : " + getClientMaxDuration() + " min", infoX, y + 156, TEXT, false);
        guiGraphics.drawString(this.font, "Coffres par joueur : " + getClientMaxChests(), infoX, y + 170, TEXT, false);
        guiGraphics.drawString(this.font, "Animaux par joueur : " + ExplorationProgression.MAX_ANIMALS_PER_PLAYER, infoX, y + 184, TEXT, false);
        if (hasExplo) {
            int totalChests = be.getSavedConfigs().stream().mapToInt(c -> c.chestCount).sum();
            int totalAnimals = be.getSavedConfigs().stream().mapToInt(c -> c.animalCount).sum();
            int cost = ExplorationCostCalculator.calculateTotalCost(be.getSavedDuration(), be.getSavedConfigs());
            guiGraphics.drawString(this.font, "Préparation", infoX, y + 210, GOLD, false);
            guiGraphics.drawString(this.font, be.getSavedDuration() + " min  J:" + be.getSavedConfigs().size() + "  C:" + totalChests + "  A:" + totalAnimals, infoX, y + 230, MUTED_TEXT, false);
            guiGraphics.drawString(this.font, "Coût : " + ExplorationCostCalculator.formatCost(cost), infoX, y + 244, GOLD, false);
        } else {
            guiGraphics.drawString(this.font, "Aucune équipe préparée.", infoX, y + 210, MUTED_TEXT, false);
        }
    }

    private void renderCreation(GuiGraphics guiGraphics, int contentX, int centerX, int y) {
        PacketExplorationAction.PlayerConfig config = playerConfigs.get(currentPlayerIndex);
        int configX = centerX + 10;
        int configW = Math.max(132, leftPos + imageWidth - 18 - configX);
        int configCenter = configX + configW / 2;

        guiGraphics.drawString(this.font, "Préparer le départ", contentX, y + 40, TEXT, false);
        guiGraphics.drawString(this.font, "Duree (max " + getClientAvailableDurations().get(getClientAvailableDurations().size() - 1) + ")", contentX, y + 54, MUTED_TEXT, false);
        drawCentered(guiGraphics, selectedDuration + " min", contentX + 88, y + 65, GOLD);
        guiGraphics.drawString(this.font, "Ajouter un joueur", contentX, y + 109, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Participants: " + playerConfigs.size(), contentX, y + 158, TEXT, false);
        for (int i = currentPlayerIndex/6*6; i < Math.min(playerConfigs.size(), currentPlayerIndex/6*6+6); i++) {
            int color = i == currentPlayerIndex ? GOLD : MUTED_TEXT;
            guiGraphics.drawString(this.font, (i + 1) + ". " + playerConfigs.get(i).playerName, contentX, y + 176 + (i%6) * 12, color, false);
        }

        guiGraphics.drawString(this.font, "Configuration joueur", configX, y + 40, TEXT, false);
        drawCentered(guiGraphics, config.playerName == null || config.playerName.isEmpty() ? "Joueur" : config.playerName, configCenter, y + 96, GOLD);
        guiGraphics.drawString(this.font, "Coffres (max " + getClientMaxChests() + ")", configX, y + 158, MUTED_TEXT, false);
        drawCentered(guiGraphics, String.valueOf(config.chestCount), configCenter, y + 178, TEXT);
        guiGraphics.drawString(this.font, "Animaux (max " + ExplorationProgression.MAX_ANIMALS_PER_PLAYER + ")", configX, y + 200, MUTED_TEXT, false);
        drawCentered(guiGraphics, String.valueOf(config.animalCount), configCenter, y + 220, TEXT);

        int cost = ExplorationCostCalculator.calculateTotalCost(selectedDuration, playerConfigs);
        guiGraphics.drawString(this.font, "Coût total : " + ExplorationCostCalculator.formatCost(cost), contentX + 96, y + imageHeight - 26, GOLD, false);
    }

    private void renderView(GuiGraphics guiGraphics, int contentX, int centerX, int y) {
        ExplorationHallBlockEntity be = getBlockEntity();
        if (be == null || !be.hasExplorationReady()) {
            guiGraphics.drawString(this.font, "Aucune équipe enregistrée.", contentX, y + 54, MUTED_TEXT, false);
            return;
        }

        int cost = ExplorationCostCalculator.calculateTotalCost(be.getSavedDuration(), be.getSavedConfigs());
        guiGraphics.drawString(this.font, "Durée : " + be.getSavedDuration() + " min", contentX, y + 48, TEXT, false);
        guiGraphics.drawString(this.font, "Total: " + ExplorationCostCalculator.formatCost(cost), contentX, y + 64, GOLD, false);
        guiGraphics.drawString(this.font, "Participants", contentX, y + 88, MUTED_TEXT, false);

        for (int i = viewPlayerIndex/6*6; i < Math.min(be.getSavedConfigs().size(), viewPlayerIndex/6*6+6); i++) {
            PacketExplorationAction.PlayerConfig row = be.getSavedConfigs().get(i);
            int color = i == viewPlayerIndex ? GOLD : statusColor(row.status);
            guiGraphics.drawString(this.font, (i + 1) + ". " + row.playerName + " - " + statusText(row.status), contentX, y + 105 + (i%6) * 13, color, false);
        }

        PacketExplorationAction.PlayerConfig config = be.getSavedConfigs().get(viewPlayerIndex);
        int playerCost = ExplorationCostCalculator.calculatePlayerCost(be.getSavedDuration(), be.getSavedConfigs().size(), config);
        drawCentered(guiGraphics, config.playerName, centerX + 68, y + 151, GOLD);
        guiGraphics.drawString(this.font, "Coffres: " + config.chestCount, centerX + 16, y + 170, TEXT, false);
        guiGraphics.drawString(this.font, "Attaches: " + config.animalCount, centerX + 16, y + 184, TEXT, false);
        guiGraphics.drawString(this.font, "Prix joueur: " + ExplorationCostCalculator.formatCost(playerCost), centerX + 16, y + 198, GOLD, false);
    }

    private void renderTalentTree(GuiGraphics guiGraphics, int contentX, int y) {
        guiGraphics.drawString(this.font, "Arbre des talents - Explorateur", contentX, y + 42, TEXT, false);
        guiGraphics.drawString(this.font, "Points disponibles : " + getAvailableTalentPoints(), leftPos + imageWidth / 2 - 48, y + 58, GREEN, false);

        int treeTop = y + 88;
        int columnGap = Math.max(118, (imageWidth - 120) / 3);
        int firstX = leftPos + imageWidth / 2 - columnGap;
        int[] xs = {firstX, firstX + columnGap, firstX + columnGap * 2};
        TalentNode[][] columns = {
                {
                        new TalentNode(0, "Temps", "d'exploration", "+ 40"),
                        new TalentNode(1, "Temps", "d'exploration", "+ 60"),
                        new TalentNode(2, "Place", "d'inventaire", "+ 1")
                },
                {
                        new TalentNode(3, "Capacite de", "stockage", "+1 -10%"),
                        new TalentNode(4, "Capacite de", "stockage", "+1 -15%"),
                        new TalentNode(5, "Place", "d'inventaire", "+ 1")
                },
                {
                        new TalentNode(6, "Lit", "d'exploration", ""),
                        new TalentNode(7, "Magnétite", "d'exploration", ""),
                        new TalentNode(8, "Place", "d'inventaire", "+ 1")
                }
        };

        for (int c = 0; c < columns.length; c++) {
            for (int row = 0; row < columns[c].length; row++) {
                int cy = treeTop + row * 64;
                drawTalentCircle(guiGraphics, xs[c], cy, columns[c][row]);
                if (row < columns[c].length - 1) {
                    drawDownArrow(guiGraphics, xs[c], cy + 32, hasTalent(columns[c][row].index()) && hasTalent(columns[c][row + 1].index()));
                }
            }
        }

        int masteryY = y + imageHeight - 66;
        drawMastery(guiGraphics, leftPos + imageWidth / 2, masteryY, hasTalent(9));
    }

    private void drawTalentCircle(GuiGraphics guiGraphics, int centerX, int centerY, TalentNode node) {
        boolean unlocked = hasTalent(node.index());
        int bg = unlocked ? 0xFF12A94F : 0xFF46505F;
        int shade = unlocked ? 0xFF0D7D3B : 0xFF303948;
        int textColor = 0xFFFFFFFF;
        drawFilledCircle(guiGraphics, centerX, centerY, 30, 0x88000000);
        drawFilledCircle(guiGraphics, centerX, centerY, 28, bg);
        guiGraphics.fill(centerX - 20, centerY + 19, centerX + 21, centerY + 23, shade);
        drawCentered(guiGraphics, node.line1(), centerX, centerY - 14, textColor);
        drawCentered(guiGraphics, node.line2(), centerX, centerY - 3, textColor);
        if (!node.line3().isEmpty()) {
            drawCentered(guiGraphics, node.line3(), centerX, centerY + 8, textColor);
        }
    }

    private void drawFilledCircle(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = (int) Math.sqrt(radius * radius - dy * dy);
            guiGraphics.fill(centerX - halfWidth, centerY + dy, centerX + halfWidth + 1, centerY + dy + 1, color);
        }
    }

    private void drawDownArrow(GuiGraphics guiGraphics, int centerX, int y, boolean unlocked) {
        int color = unlocked ? GREEN : 0xFF9AA2AE;
        guiGraphics.fill(centerX - 1, y, centerX + 1, y + 12, color);
        guiGraphics.fill(centerX - 5, y + 9, centerX - 3, y + 11, color);
        guiGraphics.fill(centerX + 3, y + 9, centerX + 5, y + 11, color);
        guiGraphics.fill(centerX - 3, y + 11, centerX + 3, y + 13, color);
    }

    private void drawMastery(GuiGraphics guiGraphics, int centerX, int centerY, boolean unlocked) {
        int bg = unlocked ? 0xFF12A94F : 0xFF46505F;
        int border = unlocked ? GREEN : 0xFF6B7585;
        int width = Math.min(260, imageWidth - 140);
        guiGraphics.fill(centerX - width / 2 - 2, centerY - 18, centerX + width / 2 + 2, centerY + 18, 0x88000000);
        guiGraphics.fill(centerX - width / 2, centerY - 16, centerX + width / 2, centerY + 16, bg);
        guiGraphics.fill(centerX - width / 2, centerY - 16, centerX + width / 2, centerY - 14, border);
        guiGraphics.fill(centerX - width / 2, centerY + 14, centerX + width / 2, centerY + 16, 0xFF202734);
        drawCentered(guiGraphics, "Maitre de guilde des explorateurs", centerX, centerY - 4, 0xFFFFFFFF);
    }

    private record TalentNode(int index, String line1, String line2, String line3) {}

    private void drawCentered(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x - this.font.width(text) / 2, y, color, false);
    }

    private int statusColor(PacketExplorationAction.InvitationStatus status) {
        return switch (status) {
            case ACCEPTED -> GREEN;
            case DECLINED -> RED;
            case PENDING -> AMBER;
        };
    }

    private String statusText(PacketExplorationAction.InvitationStatus status) {
        return switch (status) {
            case ACCEPTED -> "accepte";
            case DECLINED -> "refuse";
            case PENDING -> "attente";
        };
    }

    private enum ButtonStyle {
        PRIMARY(0xFF7B4E22, 0xFFC98536),
        SECONDARY(0xFF343434, 0xFF7B6B55),
        SUCCESS(0xFF2E6237, 0xFF74AA61),
        DANGER(0xFF6D2929, 0xFFB24A3E);

        private final int base;
        private final int border;

        ButtonStyle(int base, int border) {
            this.base = base;
            this.border = border;
        }
    }

    private class StyledButton extends Button {
        private final ButtonStyle style;

        StyledButton(int x, int y, int width, int height, Component message, OnPress onPress, ButtonStyle style) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int bg = this.active ? style.base : 0xFF242424;
            int border = this.isHoveredOrFocused() && this.active ? GOLD : style.border;
            guiGraphics.fill(x, y, x + this.width, y + this.height, 0xBB000000);
            guiGraphics.fill(x + 1, y + 1, x + this.width - 1, y + this.height - 1, bg);
            guiGraphics.fill(x, y, x + this.width, y + 1, border);
            guiGraphics.fill(x, y + this.height - 1, x + this.width, y + this.height, 0xFF17120E);
            guiGraphics.fill(x, y, x + 1, y + this.height, border);
            guiGraphics.fill(x + this.width - 1, y, x + this.width, y + this.height, 0xFF17120E);
            int color = this.active ? 0xFFFFFFFF : 0xFF8E8E8E;
            drawCentered(guiGraphics, this.getMessage().getString(), x + this.width / 2, y + (this.height - 8) / 2, color);
        }
    }
}
