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
                    
                    // Tenter d'annuler via le manager
                    int refundAmount = ExplorationManager.get(serverLevel).cancelCountdown(msg.pos);
                    
                    if (refundAmount >= 0) {
                        // Rembourser le joueur
                        MedievalCoinsIntegration.addMoney(player, refundAmount);
                        
                        player.sendSystemMessage(Component.literal("Exploration annulée. Vous avez été remboursé de " + ExplorationCostCalculator.formatCost(refundAmount)).withStyle(net.minecraft.ChatFormatting.GREEN));
                    } else {
                        player.sendSystemMessage(Component.literal("Impossible d'annuler (trop tard ou pas lancée).").withStyle(net.minecraft.ChatFormatting.RED));
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
