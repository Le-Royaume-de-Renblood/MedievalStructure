package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.gui.ExplorationHallScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncExplorationState {
    private final boolean hasExploration;

    public PacketSyncExplorationState(boolean hasExploration) {
        this.hasExploration = hasExploration;
    }

    public static void encode(PacketSyncExplorationState msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasExploration);
    }

    public static PacketSyncExplorationState decode(FriendlyByteBuf buf) {
        return new PacketSyncExplorationState(buf.readBoolean());
    }

    public static void handle(PacketSyncExplorationState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof ExplorationHallScreen screen) {
                screen.updateExplorationState(msg.hasExploration);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
