package fr.renblood.medievalstructures.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.network.PacketExplorationAction;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketLaunchExploration;
import fr.renblood.medievalstructures.network.PacketSaveExploration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExplorationHallScreen extends AbstractContainerScreen<ExplorationHallMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MedievalStructures.MODID, "textures/gui/exploration_hall_gui.png");

    private enum ScreenState {
        MAIN_MENU,
        CREATE_EXPLORATION,
        VIEW_EXPLORATION
    }

    private ScreenState currentState = ScreenState.MAIN_MENU;

    // Données de création
    private int selectedDuration = 20;
    private int selectedPlayerCount = 1;
    private List<PacketExplorationAction.PlayerConfig> playerConfigs = new ArrayList<>();
    private int currentPlayerIndex = 0;
    
    // Widgets Création
    private CycleButton<Integer> durationButton;
    private CycleButton<Integer> playerCountButton;
    private Button prevPlayerButton;
    private Button nextPlayerButton;
    private CycleButton<Integer> chestButton;
    private CycleButton<Integer> animalButton;
    private Button saveButton;
    private Button backButton;

    // Widgets Menu Principal
    private Button newExplorationButton;
    private Button launchButton;
    private Button viewButton;
    
    // Widgets Visualisation
    private Button viewBackButton;
    private Button viewPrevPlayerButton;
    private Button viewNextPlayerButton;
    private int viewPlayerIndex = 0;

    public ExplorationHallScreen(ExplorationHallMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 226; // Agrandissement
        this.imageHeight = 166;
        initPlayerConfigsData(1);
    }

    @Override
    protected void init() {
        super.init();
        initWidgets();
        updateVisibility();
    }
    
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // On ne dessine pas les titres par défaut (Inventory, etc.)
    }

    private void initWidgets() {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // Centre horizontal relatif au GUI
        int centerX = x + imageWidth / 2;

        // --- MENU PRINCIPAL ---
        newExplorationButton = addRenderableWidget(Button.builder(Component.translatable("gui.medieval_structures.exploration.new"), b -> {
            currentState = ScreenState.CREATE_EXPLORATION;
            updateVisibility();
        }).bounds(centerX - 78, y + 30, 156, 20).build());

        launchButton = addRenderableWidget(Button.builder(Component.translatable("gui.medieval_structures.exploration.launch"), this::onLaunch)
                .bounds(centerX - 78, y + 60, 156, 20).build());

        viewButton = addRenderableWidget(Button.builder(Component.translatable("gui.medieval_structures.exploration.view"), b -> {
            currentState = ScreenState.VIEW_EXPLORATION;
            viewPlayerIndex = 0;
            updateVisibility();
        }).bounds(centerX - 78, y + 90, 156, 20).build());

        // --- CRÉATION ---
        List<Integer> durations = Arrays.asList(20, 40, 60, 90, 120, 150);
        durationButton = addRenderableWidget(CycleButton.builder(this::formatDuration)
                .withValues(durations)
                .withInitialValue(selectedDuration)
                .create(centerX - 78, y + 20, 156, 20, Component.translatable("gui.medieval_structures.exploration.duration"), (cycleButton, value) -> selectedDuration = value));

        List<Integer> playerCounts = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        playerCountButton = addRenderableWidget(CycleButton.builder(this::formatPlayerCount)
                .withValues(playerCounts)
                .withInitialValue(selectedPlayerCount)
                .create(centerX - 78, y + 45, 156, 20, Component.translatable("gui.medieval_structures.exploration.players"), (cycleButton, value) -> {
                    selectedPlayerCount = value;
                    updatePlayerConfigs(value);
                }));

        prevPlayerButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (currentPlayerIndex > 0) {
                currentPlayerIndex--;
                updateCreationWidgets();
            }
        }).bounds(centerX - 78, y + 75, 20, 20).build());

        nextPlayerButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (currentPlayerIndex < playerConfigs.size() - 1) {
                currentPlayerIndex++;
                updateCreationWidgets();
            }
        }).bounds(centerX + 58, y + 75, 20, 20).build());

        List<Integer> chestCounts = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
        chestButton = addRenderableWidget(CycleButton.builder(this::formatChestCount)
                .withValues(chestCounts)
                .withInitialValue(1)
                .create(centerX - 53, y + 75, 106, 20, Component.translatable("gui.medieval_structures.exploration.chests"), (cycleButton, value) -> {
                    if (!playerConfigs.isEmpty()) playerConfigs.get(currentPlayerIndex).chestCount = value;
                }));

        List<Integer> animalCounts = Arrays.asList(1, 2, 3);
        animalButton = addRenderableWidget(CycleButton.builder(this::formatAnimalCount)
                .withValues(animalCounts)
                .withInitialValue(1)
                .create(centerX - 53, y + 100, 106, 20, Component.translatable("gui.medieval_structures.exploration.animals"), (cycleButton, value) -> {
                    if (!playerConfigs.isEmpty()) playerConfigs.get(currentPlayerIndex).animalCount = value;
                }));

        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.medieval_structures.exploration.validate"), this::onSave)
                .bounds(centerX - 78, y + 130, 75, 20).build());

        backButton = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> {
            currentState = ScreenState.MAIN_MENU;
            updateVisibility();
        }).bounds(centerX + 3, y + 130, 75, 20).build());
        
        // --- VISUALISATION ---
        viewPrevPlayerButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (viewPlayerIndex > 0) viewPlayerIndex--;
        }).bounds(centerX - 78, y + 75, 20, 20).build());

        viewNextPlayerButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            ExplorationHallBlockEntity be = getBlockEntity();
            if (be != null && viewPlayerIndex < be.getSavedConfigs().size() - 1) viewPlayerIndex++;
        }).bounds(centerX + 58, y + 75, 20, 20).build());
        
        viewBackButton = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> {
            currentState = ScreenState.MAIN_MENU;
            updateVisibility();
        }).bounds(centerX - 78, y + 130, 156, 20).build());
    }

    private void updateVisibility() {
        boolean main = currentState == ScreenState.MAIN_MENU;
        boolean create = currentState == ScreenState.CREATE_EXPLORATION;
        boolean view = currentState == ScreenState.VIEW_EXPLORATION;

        if (newExplorationButton != null) newExplorationButton.visible = main;
        if (launchButton != null) launchButton.visible = main;
        if (viewButton != null) viewButton.visible = main;

        if (durationButton != null) durationButton.visible = create;
        if (playerCountButton != null) playerCountButton.visible = create;
        if (prevPlayerButton != null) prevPlayerButton.visible = create;
        if (nextPlayerButton != null) nextPlayerButton.visible = create;
        if (chestButton != null) chestButton.visible = create;
        if (animalButton != null) animalButton.visible = create;
        if (saveButton != null) saveButton.visible = create;
        if (backButton != null) backButton.visible = create;
        
        if (viewPrevPlayerButton != null) viewPrevPlayerButton.visible = view;
        if (viewNextPlayerButton != null) viewNextPlayerButton.visible = view;
        if (viewBackButton != null) viewBackButton.visible = view;
        
        if (main && launchButton != null && viewButton != null) {
            ExplorationHallBlockEntity be = getBlockEntity();
            boolean hasExplo = be != null && be.hasExplorationReady();
            launchButton.active = hasExplo;
            viewButton.active = hasExplo;
        }
        
        if (create) {
            updateCreationWidgets();
        }
    }

    private void updateCreationWidgets() {
        if (playerConfigs.isEmpty()) return;
        if (chestButton != null) chestButton.setValue(playerConfigs.get(currentPlayerIndex).chestCount);
        if (animalButton != null) animalButton.setValue(playerConfigs.get(currentPlayerIndex).animalCount);
    }

    private void initPlayerConfigsData(int count) {
        while (playerConfigs.size() < count) {
            playerConfigs.add(new PacketExplorationAction.PlayerConfig(1, 1));
        }
        while (playerConfigs.size() > count) {
            playerConfigs.remove(playerConfigs.size() - 1);
        }
        if (currentPlayerIndex >= count) currentPlayerIndex = 0;
    }

    private void updatePlayerConfigs(int count) {
        initPlayerConfigsData(count);
        updateCreationWidgets();
    }

    private void onSave(Button button) {
        BlockPos pos = this.menu.getPos();
        if (pos != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketSaveExploration(
                    pos,
                    selectedDuration,
                    playerConfigs
            ));
            currentState = ScreenState.MAIN_MENU;
            updateVisibility();
        }
    }

    private void onLaunch(Button button) {
        BlockPos pos = this.menu.getPos();
        if (pos != null) {
            PacketHandler.INSTANCE.sendToServer(new PacketLaunchExploration(pos));
            this.onClose();
        }
    }
    
    private ExplorationHallBlockEntity getBlockEntity() {
        BlockPos pos = this.menu.getPos();
        if (pos != null && this.minecraft != null && this.minecraft.level != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(pos);
            if (be instanceof ExplorationHallBlockEntity) {
                return (ExplorationHallBlockEntity) be;
            }
        }
        return null;
    }

    private Component formatDuration(Integer value) { return Component.translatable("gui.medieval_structures.exploration.min", value); }
    private Component formatPlayerCount(Integer value) { return Component.translatable("gui.medieval_structures.exploration.player_count", value); }
    private Component formatChestCount(Integer value) { return Component.translatable("gui.medieval_structures.exploration.chest_count", value); }
    private Component formatAnimalCount(Integer value) { return Component.translatable("gui.medieval_structures.exploration.animal_count", value); }

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
        
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int centerX = x + imageWidth / 2;
        
        if (currentState == ScreenState.MAIN_MENU) {
            guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.exploration.main_menu"), x + 8, y + 6, 0x404040, false);
            // Afficher le coût ici plus tard
        } else if (currentState == ScreenState.CREATE_EXPLORATION) {
            guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.exploration.create"), x + 8, y + 6, 0x404040, false);
            Component label = Component.translatable("gui.medieval_structures.exploration.player_config", (currentPlayerIndex + 1));
            int textWidth = this.font.width(label);
            guiGraphics.drawString(this.font, label, centerX - textWidth / 2, y + 65, 0x404040, false);
        } else if (currentState == ScreenState.VIEW_EXPLORATION) {
            guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.exploration.view_title"), x + 8, y + 6, 0x404040, false);
            ExplorationHallBlockEntity be = getBlockEntity();
            if (be != null && be.hasExplorationReady()) {
                guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.exploration.duration_val", be.getSavedDuration()), x + 10, y + 30, 0x404040, false);
                guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.exploration.players_val", be.getSavedConfigs().size()), x + 10, y + 45, 0x404040, false);
                
                if (!be.getSavedConfigs().isEmpty()) {
                    PacketExplorationAction.PlayerConfig config = be.getSavedConfigs().get(viewPlayerIndex);
                    Component label = Component.translatable("gui.medieval_structures.exploration.player_config", (viewPlayerIndex + 1));
                    int textWidth = this.font.width(label);
                    guiGraphics.drawString(this.font, label, centerX - textWidth / 2, y + 65, 0x404040, false);
                    
                    guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.exploration.chests_val", config.chestCount), centerX - 53, y + 80, 0x404040, false);
                    guiGraphics.drawString(this.font, Component.translatable("gui.medieval_structures.exploration.animals_val", config.animalCount), centerX - 53, y + 105, 0x404040, false);
                }
            }
        }
    }
}
