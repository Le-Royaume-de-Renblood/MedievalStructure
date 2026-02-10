package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketLaunchExploration {
    private final BlockPos pos;

    public PacketLaunchExploration(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketLaunchExploration msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketLaunchExploration decode(FriendlyByteBuf buf) {
        return new PacketLaunchExploration(buf.readBlockPos());
    }

    public static void handle(PacketLaunchExploration msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                BlockEntity be = serverLevel.getBlockEntity(msg.pos);
                if (be instanceof ExplorationHallBlockEntity hall && hall.hasExplorationReady()) {
                    
                    // Lancer le compte à rebours
                    ExplorationManager.get(serverLevel).startCountdown(serverLevel, msg.pos, hall.getSavedDuration());
                    
                    // Réinitialiser le bloc (consommer l'exploration)
                    hall.clearExploration();
                    
                    player.sendSystemMessage(Component.literal("Lancement de l'exploration ! Restez dans la zone (7x7)."));
                    player.sendSystemMessage(Component.literal("Départ dans 30 secondes..."));
                    
                } else {
                    player.sendSystemMessage(Component.literal("Aucune exploration prête à être lancée."));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
