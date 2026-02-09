package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRequestDoorInfo {
    private final BlockPos pos;

    public PacketRequestDoorInfo(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketRequestDoorInfo msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketRequestDoorInfo decode(FriendlyByteBuf buf) {
        return new PacketRequestDoorInfo(buf.readBlockPos());
    }

    public static void handle(PacketRequestDoorInfo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                InnManager manager = InnManager.get(level);
                
                for (Inn inn : manager.getInns()) {
                    for (InnRoom room : inn.getRooms()) {
                        if (room.isInside(msg.pos)) {
                            if (room.isBusy()) {
                                player.displayClientMessage(Component.literal("Occupant : " + room.getOccupantName()), true);
                            } else {
                                player.displayClientMessage(Component.literal("Chambre " + room.getNumber() + " (Libre)"), true);
                            }
                            return;
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
