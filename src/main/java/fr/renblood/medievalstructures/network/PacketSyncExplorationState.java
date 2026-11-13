package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.gui.ExplorationHallMenu;
import fr.renblood.medievalstructures.gui.ExplorationHallScreen;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record PacketSyncExplorationState(boolean hasExploration, boolean isDebug, BlockPos pos,
        String phase, int seconds, int progress, boolean canCancel) {
    public PacketSyncExplorationState(boolean ready,boolean debug){this(ready,debug,null,"",0,0,false);}
    public PacketSyncExplorationState(boolean ready){this(ready,ExplorationManager.DEBUG_MODE);}
    public static void encode(PacketSyncExplorationState msg,FriendlyByteBuf buf){
        buf.writeBoolean(msg.hasExploration);buf.writeBoolean(msg.isDebug);buf.writeBoolean(msg.pos!=null);
        if(msg.pos!=null)buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.phase,160);buf.writeVarInt(msg.seconds);buf.writeVarInt(msg.progress);buf.writeBoolean(msg.canCancel);
    }
    public static PacketSyncExplorationState decode(FriendlyByteBuf buf){
        boolean ready=buf.readBoolean(),debug=buf.readBoolean();BlockPos pos=buf.readBoolean()?buf.readBlockPos():null;
        return new PacketSyncExplorationState(ready,debug,pos,buf.readUtf(160),Math.max(0,buf.readVarInt()),Math.max(0,Math.min(100,buf.readVarInt())),buf.readBoolean());
    }
    public static void handle(PacketSyncExplorationState msg,Supplier<NetworkEvent.Context> context){
        context.get().enqueueWork(()->{
            var mc=Minecraft.getInstance();
            if(mc.player!=null&&mc.player.containerMenu instanceof ExplorationHallMenu menu
                    &&(msg.pos==null||msg.pos.equals(menu.getPos()))){
                menu.setDebugMode(msg.isDebug);
                if(mc.screen instanceof ExplorationHallScreen screen){
                    screen.updateExplorationState(msg.hasExploration,msg.isDebug);
                    if(msg.pos!=null)screen.updateDeparture(msg.phase,msg.seconds,msg.progress,msg.canCancel);
                }
            }
        });context.get().setPacketHandled(true);
    }
}
