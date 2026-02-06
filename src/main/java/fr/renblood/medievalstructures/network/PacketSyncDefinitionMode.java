package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.manager.DefinitionModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncDefinitionMode {
    private final boolean isInMode;
    private final BlockPos point1;
    private final BlockPos point2;

    public PacketSyncDefinitionMode(boolean isInMode, BlockPos point1, BlockPos point2) {
        this.isInMode = isInMode;
        this.point1 = point1;
        this.point2 = point2;
    }

    public static void encode(PacketSyncDefinitionMode msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isInMode);
        buf.writeBoolean(msg.point1 != null);
        if (msg.point1 != null) {
            buf.writeBlockPos(msg.point1);
        }
        buf.writeBoolean(msg.point2 != null);
        if (msg.point2 != null) {
            buf.writeBlockPos(msg.point2);
        }
    }

    public static PacketSyncDefinitionMode decode(FriendlyByteBuf buf) {
        boolean isInMode = buf.readBoolean();
        BlockPos p1 = buf.readBoolean() ? buf.readBlockPos() : null;
        BlockPos p2 = buf.readBoolean() ? buf.readBlockPos() : null;
        return new PacketSyncDefinitionMode(isInMode, p1, p2);
    }

    public static void handle(PacketSyncDefinitionMode msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client side handling
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                DefinitionModeManager manager = DefinitionModeManager.getInstance();
                if (msg.isInMode) {
                    // On ne connait pas la pos du bloc structure ici, mais ce n'est pas grave pour le rendu
                    // On utilise une pos fictive ou on adapte le manager pour ne pas en avoir besoin côté client
                    if (!manager.isInDefinitionMode(mc.player)) {
                        manager.enterDefinitionMode(mc.player, BlockPos.ZERO); 
                    }
                    if (msg.point1 != null) manager.setPoint1(mc.player, msg.point1);
                    if (msg.point2 != null) manager.setPoint2(mc.player, msg.point2);
                } else {
                    manager.leaveDefinitionMode(mc.player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
