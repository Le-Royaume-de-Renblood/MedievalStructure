package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketSaveExploration {
    private final BlockPos pos;
    private final int duration;
    private final List<PacketExplorationAction.PlayerConfig> playerConfigs;

    public PacketSaveExploration(BlockPos pos, int duration, List<PacketExplorationAction.PlayerConfig> playerConfigs) {
        this.pos = pos;
        this.duration = duration;
        this.playerConfigs = playerConfigs;
    }

    public static void encode(PacketSaveExploration msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.duration);
        buf.writeInt(msg.playerConfigs.size());
        for (PacketExplorationAction.PlayerConfig config : msg.playerConfigs) {
            buf.writeInt(config.chestCount);
            buf.writeInt(config.animalCount);
        }
    }

    public static PacketSaveExploration decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int duration = buf.readInt();
        int size = buf.readInt();
        List<PacketExplorationAction.PlayerConfig> configs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            configs.add(new PacketExplorationAction.PlayerConfig(buf.readInt(), buf.readInt()));
        }
        return new PacketSaveExploration(pos, duration, configs);
    }

    public static void handle(PacketSaveExploration msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Level level = player.level();
                BlockEntity be = level.getBlockEntity(msg.pos);
                if (be instanceof ExplorationHallBlockEntity hall) {
                    hall.saveExploration(msg.duration, msg.playerConfigs);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
