package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import fr.renblood.medievalstructures.util.ExplorationCostCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketCancelExploration {
    private final BlockPos pos;

    public PacketCancelExploration(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketCancelExploration msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketCancelExploration decode(FriendlyByteBuf buf) {
        return new PacketCancelExploration(buf.readBlockPos());
    }

    public static void handle(PacketCancelExploration msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                BlockEntity be = serverLevel.getBlockEntity(msg.pos);
                if (be instanceof ExplorationHallBlockEntity hall) {
                    
                    ExplorationManager manager=ExplorationManager.get(serverLevel);
                    ExplorationManager.PendingExploration pending=manager.pendingPayment(msg.pos,player.getUUID());
                    if(pending==null&&hall.hasExplorationReady()&&player.blockPosition().closerThan(msg.pos,8.0D)
                            &&!hall.getSavedConfigs().isEmpty()&&player.getUUID().equals(hall.getSavedConfigs().get(0).playerUUID)){
                        hall.clearExploration();player.sendSystemMessage(Component.literal("Préparation effacée. Aucun paiement n’avait été effectué."));return;
                    }
                    if(pending==null||pending.paymentKey.isBlank()||!player.blockPosition().closerThan(msg.pos,8.0D)){
                        player.sendSystemMessage(Component.literal("Seul le payeur peut annuler son départ en préparation."));return;
                    }
                    if(pending.paidAmount==0&&pending.paymentKey.startsWith("free:")){
                        manager.cancelCountdown(serverLevel,msg.pos);manager.persist(serverLevel);
                        player.sendSystemMessage(Component.literal("Départ gratuit annulé. Aucun débit bancaire."));return;
                    }
                    fr.renblood.medievalstructures.integration.CommerceAdapter.refund(player,"refund:"+pending.paymentKey,"explorations",pending.paymentKey,()->{
                        if(manager.pendingPayment(msg.pos,player.getUUID())!=pending)return false;
                        return manager.cancelCountdown(serverLevel,msg.pos)>=0;
                    },()->manager.persist(serverLevel)).whenComplete((receipt,failure)->player.getServer().execute(()->{
                        player.sendSystemMessage(Component.literal(failure==null?"Annulation traitée. Consultez le reçu bancaire.":"Remboursement non terminé : vérifiez vos finances."));
                    }));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
