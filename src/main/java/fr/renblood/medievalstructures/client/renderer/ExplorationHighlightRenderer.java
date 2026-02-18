package fr.renblood.medievalstructures.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.renblood.medievalstructures.MedievalStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = MedievalStructures.MODID, value = Dist.CLIENT)
public class ExplorationHighlightRenderer {
    private static List<BlockPos> chestPositions = new ArrayList<>();
    private static int requiredChests = 0;
    private static long lastUpdateTime = 0;

    public static void updateChests(List<BlockPos> chests, int required) {
        chestPositions = chests;
        requiredChests = required;
        lastUpdateTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        if (chestPositions.isEmpty()) return;
        
        // Timeout après 10 secondes sans update (pour éviter que ça reste affiché tout le temps)
        if (System.currentTimeMillis() - lastUpdateTime > 10000) {
            chestPositions.clear();
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
        Vec3 cameraPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (int i = 0; i < chestPositions.size(); i++) {
            BlockPos pos = chestPositions.get(i);
            
            // Couleur : Vert si dans le quota requis, Rouge si en trop (ou inversement selon logique)
            // Ici : Vert = Utilisé, Bleu = Disponible mais non utilisé
            float r, g, b;
            if (i < requiredChests) {
                r = 0.0F; g = 1.0F; b = 0.0F; // Vert (Attribué)
            } else {
                r = 0.0F; g = 0.0F; b = 1.0F; // Bleu (Dispo)
            }
            
            LevelRenderer.renderLineBox(poseStack, consumer, 
                    pos.getX(), pos.getY(), pos.getZ(), 
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, 
                    r, g, b, 1.0F);
        }
        
        // Si on manque de coffres, on peut afficher des boîtes rouges fantômes ? 
        // Non, on affiche juste ceux qu'on a trouvés.

        poseStack.popPose();
    }
}
