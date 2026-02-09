package fr.renblood.medievalstructures.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.renblood.medievalstructures.block.entity.InnStructureBlockEntity;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;

public class InnStructureBlockRenderer implements BlockEntityRenderer<InnStructureBlockEntity> {
    private final Font font;

    public InnStructureBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(InnStructureBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // N'afficher que si le joueur regarde le bloc
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !((BlockHitResult) hit).getBlockPos().equals(blockEntity.getBlockPos())) {
            return;
        }

        String innName = blockEntity.getInnName();
        if (innName == null || innName.isEmpty()) return;

        // Récupérer le propriétaire (nécessite d'accéder à l'InnManager côté client, ce qui implique que les données soient synchronisées)
        // Pour l'instant, on affiche juste le nom de l'auberge car InnManager est principalement serveur.
        // Si on veut le propriétaire, il faudrait le stocker dans le TileEntity aussi.
        
        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5); // Au-dessus du bloc
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix4f = poseStack.last().pose();
        float opacity = 0.25F;
        int color = 0xFFFFFFFF;
        int backgroundColor = (int) (opacity * 255.0F) << 24;

        Component text = Component.literal(innName);
        float x = -font.width(text) / 2.0F;
        
        font.drawInBatch(text, x, 0, color, false, matrix4f, bufferSource, Font.DisplayMode.SEE_THROUGH, backgroundColor, packedLight);
        font.drawInBatch(text, x, 0, color, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);

        poseStack.popPose();
    }
}
