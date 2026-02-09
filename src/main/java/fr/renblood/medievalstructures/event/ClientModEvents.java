package fr.renblood.medievalstructures.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.item.InnkeeperWandItem;
import fr.renblood.medievalstructures.manager.DefinitionModeManager;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketRequestDoorInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MedievalStructures.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    private static List<BlockPos> highlightedProps = Collections.synchronizedList(new ArrayList<>());
    private static int highlightTimer = 0;
    
    private static BlockPos roomSelectionP1;
    private static BlockPos roomSelectionP2;

    private static BlockPos visualizedRoomP1;
    private static BlockPos visualizedRoomP2;
    private static int visualizationTimer = 0;
    
    private static int doorInfoCooldown = 0;

    public static void setHighlightedProps(List<BlockPos> props, int duration) {
        highlightedProps = Collections.synchronizedList(new ArrayList<>(props));
        highlightTimer = duration;
    }

    public static void setRoomSelection(BlockPos p1, BlockPos p2) {
        roomSelectionP1 = p1;
        roomSelectionP2 = p2;
    }

    public static void setRoomVisualization(BlockPos p1, BlockPos p2, int duration) {
        visualizedRoomP1 = p1;
        visualizedRoomP2 = p2;
        visualizationTimer = duration;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (highlightTimer > 0) {
                highlightTimer--;
                if (highlightTimer == 0) {
                    highlightedProps.clear();
                }
            }
            if (visualizationTimer > 0) {
                visualizationTimer--;
                if (visualizationTimer == 0) {
                    visualizedRoomP1 = null;
                    visualizedRoomP2 = null;
                }
            }
            if (doorInfoCooldown > 0) {
                doorInfoCooldown--;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Disable depth test manually
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        
        // Use standard lines RenderType
        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // Rendu du mode définition
        UUID playerUUID = mc.player.getUUID();
        DefinitionModeManager manager = DefinitionModeManager.getInstance();

        if (manager.isInDefinitionMode(mc.player)) {
            BlockPos pos1 = manager.getPoint1(playerUUID);
            BlockPos pos2 = manager.getPoint2(playerUUID);

            if (pos1 != null) renderBox(poseStack, buffer, new AABB(pos1), 1.0f, 0.0f, 0.0f, 1.0f);
            if (pos2 != null) renderBox(poseStack, buffer, new AABB(pos2), 0.0f, 0.0f, 1.0f, 1.0f);
            if (pos1 != null && pos2 != null) {
                AABB aabb = new AABB(pos1, pos2).expandTowards(1, 1, 1);
                renderBox(poseStack, buffer, aabb, 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }

        // Rendu de la sélection de chambre (création)
        if (roomSelectionP1 != null) renderBox(poseStack, buffer, new AABB(roomSelectionP1), 1.0f, 1.0f, 0.0f, 1.0f); // Jaune
        if (roomSelectionP2 != null) renderBox(poseStack, buffer, new AABB(roomSelectionP2), 1.0f, 1.0f, 0.0f, 1.0f); // Jaune
        if (roomSelectionP1 != null && roomSelectionP2 != null) {
            AABB aabb = new AABB(roomSelectionP1, roomSelectionP2).expandTowards(1, 1, 1);
            renderBox(poseStack, buffer, aabb, 1.0f, 1.0f, 0.0f, 1.0f); // Jaune
        }

        // Rendu de la visualisation de chambre (inspection)
        if (visualizationTimer > 0 && visualizedRoomP1 != null && visualizedRoomP2 != null) {
            AABB aabb = new AABB(visualizedRoomP1, visualizedRoomP2).expandTowards(1, 1, 1);
            renderBox(poseStack, buffer, aabb, 0.0f, 1.0f, 0.0f, 1.0f); // Vert
        }

        // Rendu de la surbillance des props
        if (highlightTimer > 0 && !highlightedProps.isEmpty()) {
            synchronized (highlightedProps) {
                for (BlockPos pos : highlightedProps) {
                    renderBox(poseStack, buffer, new AABB(pos), 0.0f, 1.0f, 1.0f, 1.0f); // Cyan
                }
            }
        }
        
        // Force draw to apply depth settings
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());

        // Re-enable depth test
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        poseStack.popPose();
        
        checkLookedBlock(mc);
    }

    private static void checkLookedBlock(Minecraft mc) {
        if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof InnkeeperWandItem)) return;
        if (doorInfoCooldown > 0) return;

        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            
            if (state.getBlock() instanceof DoorBlock) {
                PacketHandler.INSTANCE.sendToServer(new PacketRequestDoorInfo(pos));
                doorInfoCooldown = 10;
            }
        }
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer buffer, AABB aabb, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(poseStack, buffer, aabb, r, g, b, a);
    }
}
