package fr.renblood.medievalstructures.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

public class ExplorationHallBlockRenderer implements BlockEntityRenderer<ExplorationHallBlockEntity> {
    private final Font font;

    public ExplorationHallBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ExplorationHallBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.getTicksRemaining() > 0) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 1.5D, 0.5D); // Au-dessus du bloc
            poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);

            int seconds = blockEntity.getTicksRemaining() / 20;
            String text = "Départ dans " + seconds + "s";
            if (blockEntity.isWaitingForPlayers()) {
                text = "En attente de joueurs...";
            }

            Component component = Component.literal(text);
            float x = (float)(-font.width(component) / 2);
            
            Matrix4f matrix4f = poseStack.last().pose();
            float opacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
            int j = (int)(opacity * 255.0F) << 24;
            
            font.drawInBatch(component, x, 0, 553648127, false, matrix4f, bufferSource, Font.DisplayMode.SEE_THROUGH, j, packedLight);
            font.drawInBatch(component, x, 0, -1, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);

            poseStack.popPose();
        }
    }
}
