package fr.renblood.medievalstructures.client;

import fr.renblood.medievalstructures.event.ClientModEvents;
import fr.renblood.medievalstructures.gui.InnCustomerScreen;
import fr.renblood.medievalstructures.gui.InnOwnerScreen;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.manager.DefinitionModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public class ClientPacketHandler {

    public static void handleSyncDefinitionMode(boolean isInMode, BlockPos point1, BlockPos point2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            DefinitionModeManager manager = DefinitionModeManager.getInstance();
            if (isInMode) {
                if (!manager.isInDefinitionMode(mc.player)) {
                    manager.enterDefinitionMode(mc.player, BlockPos.ZERO);
                }
                if (point1 != null) manager.setPoint1(mc.player, point1);
                if (point2 != null) manager.setPoint2(mc.player, point2);
            } else {
                manager.leaveDefinitionMode(mc.player);
            }
        }
    }

    public static void handleSyncInnData(CompoundTag innData) {
        Minecraft mc = Minecraft.getInstance();
        Inn inn = Inn.load(innData);
        
        if (mc.screen instanceof InnOwnerScreen screen) {
            screen.updateInnData(inn);
        } else if (mc.screen instanceof InnCustomerScreen screen) {
            screen.updateInnData(inn);
        }
    }

    public static void handleHighlightRoom(List<BlockPos> propPositions) {
        ClientModEvents.setHighlightedProps(propPositions, 200); // 10 secondes
    }

    public static void handleSyncRoomSelection(BlockPos point1, BlockPos point2) {
        ClientModEvents.setRoomSelection(point1, point2);
    }

    public static void handleVisualizeRoom(BlockPos p1, BlockPos p2, int duration) {
        ClientModEvents.setRoomVisualization(p1, p2, duration);
    }
}
