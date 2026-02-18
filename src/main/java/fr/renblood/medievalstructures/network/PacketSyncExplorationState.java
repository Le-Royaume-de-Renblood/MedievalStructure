package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.gui.ExplorationHallMenu;
import fr.renblood.medievalstructures.gui.ExplorationHallScreen;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncExplorationState {
    private final boolean hasExploration;
    private final boolean isDebug;

    public PacketSyncExplorationState(boolean hasExploration, boolean isDebug) {
        this.hasExploration = hasExploration;
        this.isDebug = isDebug;
    }
    
    public PacketSyncExplorationState(boolean hasExploration) {
        this(hasExploration, ExplorationManager.DEBUG_MODE);
    }

    public static void encode(PacketSyncExplorationState msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasExploration);
        buf.writeBoolean(msg.isDebug);
    }

    public static PacketSyncExplorationState decode(FriendlyByteBuf buf) {
        return new PacketSyncExplorationState(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(PacketSyncExplorationState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu instanceof ExplorationHallMenu menu) {
                menu.setDebugMode(msg.isDebug);
            }

            if (Minecraft.getInstance().screen instanceof ExplorationHallScreen screen) {
                screen.updateExplorationState(msg.hasExploration, msg.isDebug);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
