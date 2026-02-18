package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketRequestChestHighlight {
    private final BlockPos pos;

    public PacketRequestChestHighlight(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketRequestChestHighlight msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketRequestChestHighlight decode(FriendlyByteBuf buf) {
        return new PacketRequestChestHighlight(buf.readBlockPos());
    }

    public static void handle(PacketRequestChestHighlight msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                BlockEntity be = serverLevel.getBlockEntity(msg.pos);
                if (be instanceof ExplorationHallBlockEntity hall) {
                    int required = 0;
                    for (PacketExplorationAction.PlayerConfig config : hall.getSavedConfigs()) {
                        required += config.chestCount;
                    }
                    
                    List<BlockPos> chests = findChests(serverLevel, msg.pos);
                    
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketHighlightChests(chests, required));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    private static List<BlockPos> findChests(ServerLevel level, BlockPos center) {
        List<BlockPos> chests = new ArrayList<>();
        // Zone : 3 au dessus, 5 en dessous, 7 de rayon autour (15x15)
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = -5; y <= 3; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos p = center.offset(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (state.getBlock() instanceof ChestBlock) {
                        chests.add(p);
                    }
                }
            }
        }
        return chests;
    }
}
