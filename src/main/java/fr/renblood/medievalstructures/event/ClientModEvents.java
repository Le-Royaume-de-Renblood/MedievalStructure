package fr.renblood.medievalstructures.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.manager.DefinitionModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MedievalStructures.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        UUID playerUUID = mc.player.getUUID();
        DefinitionModeManager manager = DefinitionModeManager.getInstance();

        if (!manager.isInDefinitionMode(mc.player)) {
            return;
        }

        BlockPos pos1 = manager.getPoint1(playerUUID);
        BlockPos pos2 = manager.getPoint2(playerUUID);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // Render Point 1
        if (pos1 != null) {
            renderBox(poseStack, buffer, new AABB(pos1), 1.0f, 0.0f, 0.0f, 1.0f);
        }

        // Render Point 2
        if (pos2 != null) {
            renderBox(poseStack, buffer, new AABB(pos2), 0.0f, 0.0f, 1.0f, 1.0f);
        }

        // Render Volume if both points are set
        if (pos1 != null && pos2 != null) {
            AABB aabb = new AABB(pos1, pos2).expandTowards(1, 1, 1);
            renderBox(poseStack, buffer, aabb, 0.0f, 1.0f, 0.0f, 1.0f);
        }

        poseStack.popPose();
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer buffer, AABB aabb, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(poseStack, buffer, aabb, r, g, b, a);
    }
}
